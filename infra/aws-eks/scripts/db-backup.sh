#!/usr/bin/env bash
#
# in-cluster Postgres 논리 백업 — pg_dump → 로컬 → S3
#
# 선행 조건 1(백업·복구 리허설, D-013). 짝: db-restore.sh
#
# ── 설계 근거 (바꾸기 전에 읽을 것) ───────────────────────────────────────
#
# ① **pg_dump 를 파드 안에서 돌린다.** pg_dump 는 자기보다 상위 버전 서버를 덤프하지 않는다:
#      pg_dump: error: server version: 17.x; pg_dump version: 15.x — aborting
#    서버는 postgres:17-alpine 이고 노트북 클라이언트 버전은 통제 밖이다. 파드 안에서
#    돌리면 버전이 정의상 일치한다.
#
# ② **`kubectl exec` 에 `-t` 를 쓰지 않는다.** TTY 가 붙으면 스트림이 텍스트 모드로
#    변환되어 `-Fc` 아카이브가 조용히 깨진다. `-i` 만 쓴다.
#
# ③ **`aws s3 cp` 는 노트북에서 한다.** alpine 이미지에 AWS CLI 가 없다. 파드에 설치하는
#    길도 있지만 그러면 파드에 S3 쓰기 권한(IRSA)이 필요해지고, 지금은 수동 절차라 불필요하다.
#
# ④ **`pg_dumpall` 을 쓰지 않는다.** 롤을 포함하면 SCRAM 해시가 덤프에 들어가 S3 로 나간다.
#    단일 DB `pg_dump` 로 한정한다.
#
# ⑤ **비밀번호도 TLS 도 쓰지 않는다.** 파드 안에서는 유닉스 소켓이고 pg_hba 가
#    `local all all trust` 다. 앱의 `sslmode=require` 는 JDBC 경로에만 해당한다.
#    🔑 뒤집어 말하면 **kubectl 접근권 = DB 무인증 superuser 접근권**이다. 이 스크립트는
#       그 사실을 만들어낸 게 아니라 드러낼 뿐이지만, 표준 절차로 승격시키는 것은 맞다.
#
# ⑥ **prod Neon 에 붙을 수 없다 — 구조적으로.** 이 스크립트는 오직 `kubectl exec` 로
#    클러스터 안 파드에 명령할 뿐 DB 호스트를 인자로 받지 않는다. "학습 클러스터를 prod DB 에
#    연결 금지" 제약이 옵션이 아니라 **불가능**으로 구현돼 있다.
#
# ⑦ 🔴 **센티넬 — 이 스크립트에서 가장 중요한 부분.**
#    학습장 DB 의 데이터는 Flyway 마이그레이션으로 **전부 재생성된다**(V11 시드 26행).
#    그래서 복구 후 "26행이 있다"는 것은 복구 성공의 증거가 **되지 못한다** — Flyway 재시드도
#    같은 결과를 낸다. 두 가설이 같은 결과를 예측하면 검사의 판정력은 0이다.
#    → 마이그레이션이 **절대 만들 수 없는** 행을 백업 직전에 심고 그 행으로만 판정한다.
#      `backup_sentinel` 테이블은 Flyway 바깥에서 만들어지며, `ddl-auto: validate` 는
#      매핑되지 않은 테이블을 보지 않으므로 앱에 영향이 없다.
#
# 사용:
#   infra/aws-eks/scripts/db-backup.sh                 # 센티넬 심고 백업 + S3 업로드
#   infra/aws-eks/scripts/db-backup.sh --no-sentinel   # 센티넬 없이 (리허설이 아닌 실백업)
#   infra/aws-eks/scripts/db-backup.sh --local-only    # S3 업로드 생략
#
set -euo pipefail

POD="${POD:-postgres-0}"
NS="${NS:-default}"
OUT_DIR="${OUT_DIR:-.claude/scratch/db-backups}"
BOOTSTRAP_DIR="${BOOTSTRAP_DIR:-infra/aws-eks/0-bootstrap}"

WITH_SENTINEL=1
UPLOAD=1
for arg in "$@"; do
  case "$arg" in
    --no-sentinel) WITH_SENTINEL=0 ;;
    --local-only)  UPLOAD=0 ;;
    -h|--help)     sed -n '2,45p' "$0"; exit 0 ;;
    *) echo "알 수 없는 인자: $arg" >&2; exit 2 ;;
  esac
done

die() { echo "🔴 $*" >&2; exit 1; }
say() { echo "  $*"; }

# ── 사전 조건 ────────────────────────────────────────────────────────────
command -v kubectl >/dev/null || die "kubectl 이 없다"
[ "$UPLOAD" -eq 1 ] && { command -v aws >/dev/null || die "aws CLI 가 없다 (--local-only 로 우회 가능)"; }

kubectl -n "$NS" get pod "$POD" >/dev/null 2>&1 \
  || die "파드 $NS/$POD 를 찾을 수 없다 — 클러스터가 떠 있나?"

phase="$(kubectl -n "$NS" get pod "$POD" -o jsonpath='{.status.phase}')"
[ "$phase" = "Running" ] || die "파드가 Running 이 아니다 (현재: $phase)"

# pg_isready 로 "포트가 열렸다"가 아니라 "접속을 받을 준비가 됐다"를 확인한다.
kubectl -n "$NS" exec "$POD" -- sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null \
  || die "postgres 가 접속을 받을 준비가 안 됐다"

mkdir -p "$OUT_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP="$OUT_DIR/postgres-$STAMP.dump"
MANIFEST="$OUT_DIR/postgres-$STAMP.manifest"

# ── ① 센티넬 심기 ────────────────────────────────────────────────────────
SENTINEL=""
if [ "$WITH_SENTINEL" -eq 1 ]; then
  # 토큰은 노트북에서 만든다 — 클러스터 안에서 만들면 "복구된 값"과 "새로 만든 값"을
  # 구별할 근거가 클러스터 안에만 있게 된다. 판정 기준은 바깥에 있어야 한다.
  SENTINEL="sentinel-$STAMP-$(head -c 8 /dev/urandom | od -An -tx1 | tr -d ' \n')"

  # 🔴 `kubectl exec` 에는 **`--env` 플래그가 없다**(있는 것은 -c/-f/--pod-running-timeout/-q 뿐).
  #    쓰면 `unknown flag: --env` 로 죽는다. 값은 **stdin 으로 흘려보낸다** — 셸 인용 중첩도
  #    같이 사라지므로 `'"'"'` 지옥을 피할 수 있다.
  printf '%s\n' \
  "CREATE TABLE IF NOT EXISTS backup_sentinel (id bigserial PRIMARY KEY, token text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());" \
  "INSERT INTO backup_sentinel (token) VALUES ('$SENTINEL');" \
  | kubectl -n "$NS" exec -i "$POD" -- sh -c \
    'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f -' \
  || die "센티넬 삽입 실패"

  # 심었다는 것을 **덤프 전에** 확인한다. 여기서 못 찾으면 덤프에도 없다.
  found="$(printf "SELECT count(*) FROM backup_sentinel WHERE token = '%s';\n" "$SENTINEL" \
  | kubectl -n "$NS" exec -i "$POD" -- sh -c \
      'psql -tAq -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f -' | tr -d '[:space:]')"
  [ "$found" = "1" ] || die "센티넬을 심었는데 조회되지 않는다 (count=$found)"
  say "① 센티넬 심음: $SENTINEL"
fi

# ── ② pg_dump ───────────────────────────────────────────────────────────
# -Fc  custom format: 압축 + pg_restore 의 --exit-on-error/--single-transaction 사용 가능.
#      plain SQL 로 뽑으면 복구가 `psql` 이 되는데, psql 은 기본적으로 ON_ERROR_STOP 이
#      꺼져 있어 **에러를 뱉으며 끝까지 돌고 exit 0** 을 낸다 = 조용한 부분 복구.
# --no-owner / --no-privileges: 복구 대상이 같은 롤이므로 불필요하고, 넣어두면 롤 이름이
#      덤프에 박혀 나중에 롤을 바꿀 때 걸린다.
kubectl -n "$NS" exec -i "$POD" -- sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-privileges' > "$DUMP" \
  || die "pg_dump 실패"

[ -s "$DUMP" ] || die "덤프가 비어 있다"

# 덤프가 **읽히는지** 확인한다. 파일 크기만 보면 깨진 아카이브를 통과시킨다
# (`-t` 를 붙였을 때 정확히 이렇게 된다 — 크기는 그럴듯한데 pg_restore 가 못 읽는다).
objects="$(pg_restore -l "$DUMP" 2>/dev/null | grep -cv '^;' || true)"
if [ -z "$objects" ] || [ "$objects" -eq 0 ]; then
  # 노트북에 pg_restore 가 없을 수 있다 — 그때는 파드로 되돌려 검사한다.
# 🔴 **파일명을 주지 않는다.** `/dev/stdin` 을 *파일 이름으로* 넘기면 pg_restore 가 그것을
#    열어 seek 하려 드는데 파이프는 seek 이 안 된다 → `did not find magic string in file
#    header` (2026-09-13 실측). 파일명을 생략하면 stdin 을 **스트리밍 모드**로 읽는다.
#    ⚠️ 덤프는 멀쩡한데 검사가 깨져서 나는 에러라 메시지가 정반대를 가리킨다.
objects="$(kubectl -n "$NS" exec -i "$POD" -- pg_restore -l < "$DUMP" 2>/dev/null | grep -cv '^;' || true)"
fi
[ "${objects:-0}" -gt 0 ] || die "덤프를 pg_restore 가 읽지 못한다 — 아카이브가 깨졌다"

size="$(wc -c < "$DUMP" | tr -d ' ')"
say "② 덤프 생성: $DUMP (${size} bytes, 객체 ${objects}개)"

# ── ③ 매니페스트 ────────────────────────────────────────────────────────
# 판정 기준(센티넬 토큰)을 덤프 **바깥**에 남긴다. 덤프 안에만 있으면 "덤프를 열어봐야
# 판정 기준을 알 수 있는" 순환이 된다.
{
  echo "created_at=$STAMP"
  echo "dump_file=$(basename "$DUMP")"
  echo "dump_bytes=$size"
  echo "dump_objects=$objects"
  echo "sentinel_token=${SENTINEL:-none}"
  echo "source=in-cluster $NS/$POD"
} > "$MANIFEST"

# ── ④ S3 업로드 ─────────────────────────────────────────────────────────
if [ "$UPLOAD" -eq 1 ]; then
  BUCKET="${BACKUP_BUCKET:-}"
  if [ -z "$BUCKET" ]; then
    BUCKET="$(cd "$BOOTSTRAP_DIR" && tofu output -raw backup_bucket 2>/dev/null || true)"
  fi
  # 🔴 빈 값 가드. 없으면 `s3://` 로 시작하는 쓰레기 경로에 업로드를 시도하거나,
  #    더 나쁘게는 성공한 것처럼 보이는 경로가 만들어진다. SOP §2b 가 같은 함정을 이미 잡았다.
  [ -n "$BUCKET" ] || die "백업 버킷 이름을 알 수 없다 (BACKUP_BUCKET 환경변수 또는 0-bootstrap tofu output)"

  aws s3 cp "$DUMP"     "s3://$BUCKET/postgres/$(basename "$DUMP")"     --only-show-errors || die "S3 업로드 실패"
  aws s3 cp "$MANIFEST" "s3://$BUCKET/postgres/$(basename "$MANIFEST")" --only-show-errors || die "S3 매니페스트 업로드 실패"

  # 업로드했다고 믿지 않는다 — 되읽어 크기를 대조한다.
  remote_size="$(aws s3api head-object --bucket "$BUCKET" --key "postgres/$(basename "$DUMP")" \
                   --query 'ContentLength' --output text 2>/dev/null || echo 0)"
  [ "$remote_size" = "$size" ] || die "S3 객체 크기가 로컬과 다르다 (로컬 $size / 원격 $remote_size)"
  say "③ S3 업로드 확인: s3://$BUCKET/postgres/$(basename "$DUMP") (${remote_size} bytes)"
fi

echo
echo "✅ 백업 완료"
echo "   덤프      $DUMP"
echo "   매니페스트 $MANIFEST"
[ -n "$SENTINEL" ] && echo "   센티넬    $SENTINEL   ← 복구 판정은 이 토큰으로만 한다"
exit 0
