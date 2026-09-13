#!/usr/bin/env bash
#
# in-cluster Postgres 논리 복구 — S3/로컬 덤프 → pg_restore
#
# 선행 조건 1(백업·복구 리허설, D-013). 짝: db-backup.sh
#
# ── 🔴 이 스크립트가 막는 실패 (전부 실측 근거가 있는 것들) ────────────────
#
# ① **조용한 부분 복구.** `psql` 은 기본 `ON_ERROR_STOP` 이 꺼져 있어 에러를 뱉으며 끝까지
#    돌고 **exit 0** 을 낸다. 그리고 `ddl-auto: validate` 는 **테이블 구조만** 보므로 행이
#    반쯤 없어도 앱은 정상 기동한다 → 아무도 모른다.
#    → `-Fc` 아카이브 + `pg_restore --exit-on-error --single-transaction`. 전부 아니면 전무.
#
# ② **앱이 먼저 뜨면 Flyway 가 스키마를 올려버린다.** 그 뒤 복구는 `relation already exists`
#    로 막힌다. → 복구 전 core-api·daily-api 를 **둘 다** replicas=0 으로 내린다.
#
# ③ 🔴 **daily-api 를 빼먹으면 최악의 사고가 난다.** daily-api 도 같은 `core-api-db` Secret
#    을 본다. 그리고 마이그레이션이 **두 모듈에 쪼개져 있다**(core-api V1~V6·V8·V9 /
#    db-core V7·V10~V13). 복구한 `flyway_schema_history` 를 들고 daily-api 에서 migrate 가
#    돌면 `repair()` 가 core-api 버전을 `DELETED` 로 마킹 → **core-api 영구 부팅 불가**
#    (2026-07-01 V8 사고와 동일한 형태). 리허설 중 "마이그레이션을 다시 돌려보자"는 유혹이
#    정확히 이 버튼이다.
#
# ④ **판정력 0인 성공 판정.** 복구 후 "26행이 있다"는 Flyway 재시드로도 똑같이 성립한다.
#    → 판정은 **센티넬 토큰으로만** 한다. 그리고 `--expect-absent` 로 복구 **전** 부재를
#      먼저 확인해야 "애초에 안 지워졌다"와 구별된다.
#
# 사용:
#   # 볼륨 파괴 직후, 복구 전 — 판정력의 원천
#   infra/aws-eks/scripts/db-restore.sh --expect-absent <TOKEN>
#
#   # 복구
#   infra/aws-eks/scripts/db-restore.sh --dump <파일> --sentinel <TOKEN>
#   infra/aws-eks/scripts/db-restore.sh --s3 postgres-2026....dump --sentinel <TOKEN>
#
set -euo pipefail

POD="${POD:-postgres-0}"
NS="${NS:-default}"
OUT_DIR="${OUT_DIR:-.claude/scratch/db-backups}"
BOOTSTRAP_DIR="${BOOTSTRAP_DIR:-infra/aws-eks/0-bootstrap}"
APPS="${APPS:-core-api daily-api}"

MODE="restore"
DUMP=""
S3_KEY=""
SENTINEL=""

while [ $# -gt 0 ]; do
  case "$1" in
    --expect-absent) MODE="expect-absent"; SENTINEL="${2:-}"; shift 2 ;;
    --dump)          DUMP="${2:-}";        shift 2 ;;
    --s3)            S3_KEY="${2:-}";      shift 2 ;;
    --sentinel)      SENTINEL="${2:-}";    shift 2 ;;
    -h|--help)       sed -n '2,40p' "$0"; exit 0 ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done

die() { echo "🔴 $*" >&2; exit 1; }
say() { echo "  $*"; }

command -v kubectl >/dev/null || die "kubectl 이 없다"
kubectl -n "$NS" get pod "$POD" >/dev/null 2>&1 || die "파드 $NS/$POD 를 찾을 수 없다"
kubectl -n "$NS" exec "$POD" -- sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null \
  || die "postgres 가 접속을 받을 준비가 안 됐다"

# 🔴 `kubectl exec` 에 `--env` 플래그는 **없다**. 토큰은 stdin 으로 흘려보낸다.
#
# 🔴 **한 문장으로 분기할 수 없다 (2026-09-13 실측).** 처음엔 이렇게 썼다:
#      SELECT CASE WHEN to_regclass('public.backup_sentinel') IS NULL THEN 0
#                  ELSE (SELECT count(*) FROM backup_sentinel WHERE ...) END;
#    *"to_regclass 가 NULL 을 주니 예외 없이 분기된다"* 고 생각했는데 **틀렸다.**
#    PostgreSQL 은 실행 전에 **문장 전체를 파싱·플랜**하므로, 타지 않는 분기 안의
#    테이블 이름도 그 시점에 해석된다:
#      ERROR: relation "backup_sentinel" does not exist
#    CASE 는 런타임 분기이지 파스타임 보호가 아니다. → **두 문장으로 나눈다.**
#
# 🔴 그리고 그때 psql 은 **ERROR 를 뱉고도 exit 0** 을 냈다 — 이 스크립트가 복구 경로에
#    대해 경고하는 바로 그 함정이다. 판정 경로에도 `ON_ERROR_STOP=1` 을 건다.
#
# 반환: 정수, 또는 조회 자체가 실패하면 **빈 문자열**. 호출자가 둘을 구분해야 한다 —
#      "센티넬이 없다"와 "확인할 수 없다"는 다른 사실이고, 섞으면 SOP §2b 가 경고하는
#      *"가장 위험한 방향으로 조용히 통과하는 검사"* 가 된다.
psql_q() {
  kubectl -n "$NS" exec -i "$POD" -- sh -c \
    'psql -v ON_ERROR_STOP=1 -tAq -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f -' 2>/dev/null | tr -d '[:space:]'
}

sentinel_count() {
  local exists
  exists="$(printf "SELECT to_regclass('public.backup_sentinel') IS NOT NULL;\n" | psql_q)"
  case "$exists" in
    f) echo 0; return 0 ;;                 # 테이블 자체가 없다 = 센티넬 0개
    t) ;;                                  # 있다 → 아래에서 센다
    *) echo ""; return 0 ;;                # 판정 불가 (접속 실패 등)
  esac
  printf "SELECT count(*) FROM backup_sentinel WHERE token = '%s';\n" "$1" | psql_q
}

# ── 모드 A: 복구 전 부재 확인 ────────────────────────────────────────────
#
# 🔑 **이 단계가 리허설 전체의 판정력이다.** 없으면 "복구가 됐다"와 "애초에 안 지워졌다"가
#    구별되지 않는다. 볼륨을 아무리 세게 파괴해도 이 확인이 없으면 검사는 무력하다.
if [ "$MODE" = "expect-absent" ]; then
  [ -n "$SENTINEL" ] || die "--expect-absent 에는 토큰이 필요하다"
  n="$(sentinel_count "$SENTINEL")"
  if [ -z "$n" ]; then
    die "🟡 판정 불가 — 센티넬 조회 자체가 실패했다. 센티넬이 없다는 뜻이 **아니다.**
       postgres 접속·DB 이름을 확인하고 다시 실행할 것. (멈추는 것이 맞다 — 이 확인을
       건너뛰면 이후 복구 결과가 무엇을 증명하는지 말할 수 없게 된다.)"
  fi
  if [ "$n" != "0" ]; then
    die "센티넬이 **아직 있다** (count=$n). 볼륨이 실제로 파괴되지 않았거나 다른 볼륨을 보고 있다.
       이 상태로 복구를 진행하면 그 뒤 무슨 결과가 나오든 복구의 증거가 되지 못한다."
  fi
  echo "✅ 센티넬 부재 확인 — 볼륨이 비었다. 이제 복구 결과가 증거가 된다."
  exit 0
fi

# ── 모드 B: 복구 ─────────────────────────────────────────────────────────
[ -n "$SENTINEL" ] || die "--sentinel 토큰이 필요하다 (판정 기준 없이 복구하면 성공 여부를 알 수 없다)"

if [ -n "$S3_KEY" ]; then
  command -v aws >/dev/null || die "aws CLI 가 없다"
  BUCKET="${BACKUP_BUCKET:-}"
  [ -n "$BUCKET" ] || BUCKET="$(cd "$BOOTSTRAP_DIR" && tofu output -raw backup_bucket 2>/dev/null || true)"
  [ -n "$BUCKET" ] || die "백업 버킷 이름을 알 수 없다"
  mkdir -p "$OUT_DIR"
  DUMP="$OUT_DIR/$(basename "$S3_KEY")"
  aws s3 cp "s3://$BUCKET/postgres/$(basename "$S3_KEY")" "$DUMP" --only-show-errors || die "S3 다운로드 실패"
  say "S3 에서 받음: $DUMP"
fi

[ -n "$DUMP" ] || die "--dump 또는 --s3 중 하나가 필요하다"
[ -s "$DUMP" ] || die "덤프 파일이 없거나 비어 있다: $DUMP"

# ── ① 앱 정지 — ②③ 방지 ────────────────────────────────────────────────
scaled=""
for app in $APPS; do
  if kubectl -n "$NS" get deploy "$app" >/dev/null 2>&1; then
    prev="$(kubectl -n "$NS" get deploy "$app" -o jsonpath='{.spec.replicas}')"
    scaled="$scaled $app=$prev"
    kubectl -n "$NS" scale deploy "$app" --replicas=0 >/dev/null
  fi
done
[ -n "$scaled" ] && say "① 앱 정지:$scaled (복구 후 되돌린다)"

# 파드가 실제로 사라질 때까지 기다린다. scale 은 즉시 반환하지만 커넥션은 남아 있고,
# 남은 커넥션은 복구 도중 Flyway 를 돌리거나 락을 잡는다.
for app in $APPS; do
  kubectl -n "$NS" wait --for=delete pod -l "app=$app" --timeout=90s >/dev/null 2>&1 || true
done

# ── ② 복구 ──────────────────────────────────────────────────────────────
# --single-transaction : 하나라도 실패하면 전부 롤백. 반쯤 복구된 DB 가 남지 않는다.
# --exit-on-error      : psql 의 ON_ERROR_STOP 에 해당. 없으면 에러를 뱉으며 exit 0.
# --clean --if-exists  : 기존 객체를 지우고 덮는다. 갓 initdb 된 DB 면 no-op.
#
# ⚠️ `--clean` 과 `--single-transaction` 을 같이 쓰면 DROP 도 트랜잭션 안이라
#    "지우다 말았다"가 생기지 않는다. 둘 중 하나만 쓰면 그 구멍이 생긴다.
if ! kubectl -n "$NS" exec -i "$POD" -- sh -c \
     'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges --single-transaction --exit-on-error' \
     < "$DUMP"; then
  die "pg_restore 실패 — 트랜잭션이 롤백됐으므로 DB 는 복구 시도 전 상태다"
fi
say "② pg_restore 완료 (single-transaction)"

# ── ③ 판정 — 센티넬로만 한다 ────────────────────────────────────────────
n="$(sentinel_count "$SENTINEL")"
[ "$n" = "1" ] || die "센티넬을 찾을 수 없다 (count=$n). 복구가 실패했거나 다른 덤프다."
say "③ 센티넬 확인: $SENTINEL"

# 보조 지표 — **판정 기준이 아니다.** Flyway 로도 같은 값이 나오므로 참고만 한다.
rows="$(printf "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';\n" \
  | kubectl -n "$NS" exec -i "$POD" -- sh -c \
      'psql -tAq -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f -' | tr -d '[:space:]')"
say "   (참고) public 스키마 테이블 ${rows}개 — 판정 기준 아님"

# ── ④ 앱 복원 ───────────────────────────────────────────────────────────
for entry in $scaled; do
  app="${entry%%=*}"; n_prev="${entry##*=}"
  kubectl -n "$NS" scale deploy "$app" --replicas="$n_prev" >/dev/null
done
[ -n "$scaled" ] && say "④ 앱 복원:$scaled"

echo
echo "✅ 복구 완료 — 판정 근거는 센티넬 $SENTINEL"
exit 0
