#!/usr/bin/env bash
# Neon(현 prod) → EC2 가져오기 — 📌D-016 6-C 리허설 · 7번 데이터 이전. 손으로만 돈다(timer 없음).
#
#   Neon **읽기 전용** role 로 스냅샷 덤프 ──스트리밍──▶ EC2 `devquest` 에 pg_restore(소유자 devquest)
#   판정 = Neon 의 같은 스냅샷 행 수 ↔ EC2 복원본 행 수 + Flyway 이력 지문
#
# 🔴 Neon 에 **쓰지 않는다** — 이 스크립트에 Neon 으로 향하는 쓰기 명령은 없고, 접속 role 도 쓰기 권한이
#    없어야 한다(아래 검사). 쓰기 role 을 EC2 에 두면 스크립트 버그 하나가 prod 삭제다(B6-12).
# 🔴 전환(7번) 뒤에는 **절대 돌지 않는다** — `CUTOVER_DONE=1` 이면 거부. 그때 EC2 가 prod 원본이라
#    `--reset` 한 번이 prod 를 지운다.
# 접속 정보: SSM `<SSM_PREFIX>/neon-source-url` (사용자가 콘솔에서 넣는다 — TASK-14). 값은 argv 에 두지 않는다
#   (ps 에 보인다) — 서브셸 안에서만 PG* 환경변수로 export 하고, 로컬 pg_restore 쪽엔 새지 않게 한다.
#
# 사용: neon-import.sh            대상 devquest 가 비어 있을 때만
#       neon-import.sh --reset    devquest 를 지우고 다시 만든 뒤 가져온다(리허설 반복용, 전환 전만)
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$here/common.sh"

RESET=0
case "${1:-}" in --reset) RESET=1 ;; "") ;; *) die "알 수 없는 인자: $1" ;; esac
RO_USER="${NEON_RO_USER:-devquest_ro}"

[ "$CUTOVER_DONE" = "0" ] || die "전환(7번) 완료 표시 CUTOVER_DONE=$CUTOVER_DONE — EC2 가 prod 원본이다. 가져오기 금지"
! cutover_done || die "전환(7번) 완료 표식이 데이터 볼륨에 있다($CUTOVER_MARK) — EC2 가 prod 원본이다. 가져오기 금지"

URL=$(aws ssm get-parameter --region "$REGION" --name "$SSM_PREFIX/neon-source-url" --with-decryption \
        --query Parameter.Value --output text) || die "SSM $SSM_PREFIX/neon-source-url 없음 — TASK-14"
re='^postgres(ql)?://([^:@/]+):([^@/]+)@([^:/?]+)(:([0-9]+))?/([^?]+)(\?.*)?$'
[[ "$URL" =~ $re ]] || die "neon-source-url 형식이 postgresql://user:pass@host/db 가 아니다(값은 출력하지 않는다)"
N_USER="${BASH_REMATCH[2]}"; N_PASS="${BASH_REMATCH[3]}"; N_HOST="${BASH_REMATCH[4]}"
N_PORT="${BASH_REMATCH[6]:-5432}"; N_DB="${BASH_REMATCH[7]}"
unset URL
[ "$N_USER" = "$RO_USER" ] || die "Neon 접속 role 이 $RO_USER 가 아니다($N_USER) — 읽기 전용 role 만 쓴다(B6-12)"
[[ "$N_HOST" != *-pooler* ]] || die "pooler 호스트다 — 스냅샷 공유가 안 된다. 직접 호스트를 쓴다(TASK-14 3)"

neon_env() {  # 서브셸 안에서만 호출한다
  export PGHOST="$N_HOST" PGPORT="$N_PORT" PGUSER="$N_USER" PGDATABASE="$N_DB" PGSSLMODE=require
  PGPASSWORD="$(printf '%b' "${N_PASS//%/\\x}")"; export PGPASSWORD
}

# 읽기 전용 확인 — 쓰기 권한이 하나라도 있으면 멈춘다.
W=$( neon_env; psql -X -q -A -t -v ON_ERROR_STOP=1 -c \
  "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE c.relkind IN ('r','p') AND n.nspname NOT IN ('pg_catalog','information_schema')
      AND (has_table_privilege(c.oid,'INSERT') OR has_table_privilege(c.oid,'UPDATE')
        OR has_table_privilege(c.oid,'DELETE') OR has_table_privilege(c.oid,'TRUNCATE'))" ) \
  || die "Neon 접속 실패(호스트·비번·네트워크)"
[ "$W" = "0" ] || die "Neon role $N_USER 에 쓰기 권한이 있는 테이블 ${W}개 — 읽기 전용 role 이 아니다(B6-12)"
log "Neon 접속·읽기 전용 확인 — $N_HOST/$N_DB"

# 🔴 대상 DB 를 비우는 것은 **원천 검사를 전부 통과한 뒤**다(테스트 ④ 가 실제로 잡은 순서 버그 — 처음엔
#    --reset 이 맨 앞이라 Neon role 이 쓰기 권한이어서 거부될 때도 대상은 이미 지워져 있었다).
n=$(user_table_count "$DB")
if [ "$n" != "0" ]; then
  [ "$RESET" = "1" ] || die "대상 $DB 에 테이블 ${n}개 — 비어 있지 않다. 리허설 반복이면 --reset"
  # 세 번째 가드(QA F-2): 앱이 붙어 있으면 지우지 않는다. 전환 전엔 Fly·EKS 가 이 DB 를 쓰지 않는다.
  live=$("${AS_PG[@]}" psql -X -q -A -t -v ON_ERROR_STOP=1 -d postgres -c \
    "SELECT count(*) FROM pg_stat_activity WHERE datname = '$DB' AND client_addr IS NOT NULL")
  [ "$live" = "0" ] || die "$DB 에 원격 접속 ${live}개 — 앱이 쓰는 DB 다. --reset 거부"
  log "--reset: $DB 를 지우고 다시 만든다(전환 전 리허설)"
  "${AS_PG[@]}" psql -X -q -v ON_ERROR_STOP=1 -d postgres \
    -c "DROP DATABASE \"$DB\" WITH (FORCE)" -c "CREATE DATABASE \"$DB\" OWNER devquest" >/dev/null
fi

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
START=$(date +%s)
# 왼쪽 서브셸에만 PG* — 오른쪽 로컬 pg_restore 가 Neon 으로 향하지 않게 env -u 로 한 번 더 지운다.
# shellcheck disable=SC2034  # snapshot_dump 의 입력
( neon_env; PSQL_PREFIX=(); PSQL_CONN=(); snapshot_dump "$TMP/expected" ) \
  | env -u PGHOST -u PGPORT -u PGUSER -u PGDATABASE -u PGPASSWORD -u PGSSLMODE \
      "${AS_PG[@]}" pg_restore --no-owner --no-acl --role=devquest --single-transaction --exit-on-error -d "$DB"
SECS=$(( $(date +%s) - START ))

local_counts "$DB" > "$TMP/actual"
diff -u "$TMP/expected" "$TMP/actual" >&2 || die "EC2 복원본이 Neon 스냅샷과 다르다(위 diff: - Neon, + EC2)"
local_seq_check "$DB" || die "가져온 DB 의 시퀀스가 데이터보다 뒤처졌다(위) — 전환하면 첫 INSERT 가 id 충돌한다"
TABLES=$(grep -vc '^flyway|' "$TMP/actual" || true)
health neon-import "secs=$SECS" "tables=$TABLES"
log "✅ Neon → EC2 가져오기 판정 통과 — ${SECS}s, 테이블 ${TABLES}개, Flyway 이력 일치"
