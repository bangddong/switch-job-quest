#!/usr/bin/env bash
# Neon 콜드 대기 복원 — 📌D-016 Q-2. 최신 S3 덤프(`.counts` 짝)를 **Neon 대기 DB** 에 덮어쓰고 값으로 판정한다.
#
#   6-C(2026-10-20): Neon **브랜치** `standby-rehearsal` 에 손으로 1회 — 도구 리허설.
#   7번 전환 뒤: 매일 timer 로(옛 prod Neon 이 계정 밖 사본이 된다). 그 배선은 7번 범위.
#
# 🔴 prod Neon(main 브랜치)을 덮어쓰지 않는 것이 이 스크립트의 전부다(B6-1). 가드:
#    ① 접속 role 이름이 `devquest_standby` + 접속해 보니 **neon_superuser 소속이 아님** — Neon 브랜치에서
#       **SQL 로 만든** role 은 그 브랜치에만 있다(콘솔 role 은 neon_superuser 자동 소속 — 6-0 실측)
#    ② 대기 호스트 ≠ prod 호스트 — 브랜치마다 엔드포인트가 다르다. 같은 호스트면 prod 일 수 있다
#    ③ 대기 DB 이름 ≠ prod DB 이름
#    prod 좌표는 전환(7번) 전엔 SSM `neon-source-url` 에서, 그 파라미터를 지운 뒤엔 `NEON_PROD_HOST`·`NEON_PROD_DB` 로.
#    **좌표를 모르면 덮어쓰지 않는다**(대조 없이 진행하지 않는다).
# 접속 정보: SSM `<SSM_PREFIX>/neon-standby-url`. 값은 argv·로그에 두지 않는다(neon-import.sh 와 같은 방식).
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$here/common.sh"

STANDBY_USER="${NEON_STANDBY_USER:-devquest_standby}"
re='^postgres(ql)?://([^:@/]+):([^@/]+)@([^:/?]+)(:([0-9]+))?/([^?]+)(\?.*)?$'
get_url() { aws ssm get-parameter --region "$REGION" --name "$SSM_PREFIX/$1" --with-decryption --query Parameter.Value --output text 2>/dev/null; }

URL=$(get_url neon-standby-url) || die "SSM $SSM_PREFIX/neon-standby-url 없음"
[[ "$URL" =~ $re ]] || die "neon-standby-url 형식이 postgresql://user:pass@host/db 가 아니다(값은 출력하지 않는다)"
S_USER="${BASH_REMATCH[2]}"; S_PASS="${BASH_REMATCH[3]}"; S_HOST="${BASH_REMATCH[4]}"
S_PORT="${BASH_REMATCH[6]:-5432}"; S_DB="${BASH_REMATCH[7]}"
unset URL

P_HOST="${NEON_PROD_HOST:-}"; P_DB="${NEON_PROD_DB:-}"
if SRC=$(get_url neon-source-url) && [[ "$SRC" =~ $re ]]; then
  P_HOST="${P_HOST:-${BASH_REMATCH[4]}}"; P_DB="${P_DB:-${BASH_REMATCH[7]}}"
fi
unset SRC
[ -n "$P_HOST" ] && [ -n "$P_DB" ] || die "prod Neon 좌표를 모른다 — neon-source-url 도 NEON_PROD_HOST·NEON_PROD_DB 도 없다. 대조 없이 덮어쓰지 않는다"

[ "$S_USER" = "$STANDBY_USER" ] || die "대기 role 이 $STANDBY_USER 가 아니다($S_USER) — 브랜치 전용 role 만 쓴다"
[ "$S_HOST" != "$P_HOST" ] || die "대기 호스트가 prod 호스트와 같다 — prod 를 덮어쓸 수 있다. 중단"
[ "$S_DB" != "$P_DB" ] || die "대기 DB 이름이 prod DB 이름과 같다($S_DB) — 중단"
[[ "$S_HOST" != *-pooler* ]] || die "pooler 호스트다 — 직접 호스트를 쓴다"

standby_env() {  # 서브셸 안에서만
  export PGHOST="$S_HOST" PGPORT="$S_PORT" PGUSER="$S_USER" PGDATABASE="$S_DB" PGSSLMODE=require
  PGPASSWORD="$(printf '%b' "${S_PASS//%/\\x}")"; export PGPASSWORD
}

CHK=$( standby_env; psql -X -q -A -t -v ON_ERROR_STOP=1 -c \
  "SELECT current_user || '|' || current_database() || '|' ||
          (SELECT count(*) FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.roleid
            WHERE m.member = (SELECT oid FROM pg_roles WHERE rolname = current_user) AND r.rolname = 'neon_superuser')" ) \
  || die "Neon 대기 DB 접속 실패(호스트·비번·네트워크)"
[ "$CHK" = "$STANDBY_USER|$S_DB|0" ] || die "대기 접속 확인 실패(${CHK%%|*}…) — role·DB 가 다르거나 neon_superuser 소속이다"
log "Neon 대기 접속 확인 — $S_HOST/$S_DB (prod 호스트와 다름)"

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
CKEY=$(aws s3api list-objects-v2 --region "$REGION" --bucket "$BUCKET" --prefix "${PREFIX}dumps/" \
        --query "sort_by(Contents[?ends_with(Key, '.counts')], &Key)[-1].Key" --output text)
[[ "$CKEY" == "${PREFIX}dumps/devquest-"*.counts ]] || die "완결된 덤프(.counts 짝)가 없다(${CKEY:-없음})"
aws s3 cp --region "$REGION" --only-show-errors "s3://$BUCKET/$CKEY" "$TMP/expected"
log "복원 대상: ${CKEY%.counts}.dump → Neon 대기"

START=$(date +%s)
# --clean --if-exists: 이전 복원본을 지우고 덮어쓴다(대기 DB 소유자가 이 role). 한 트랜잭션 — 실패하면 이전 것이 남는다.
aws s3 cp --region "$REGION" --only-show-errors "s3://$BUCKET/${CKEY%.counts}.dump" - \
  | ( standby_env; pg_restore --clean --if-exists --no-owner --no-acl --single-transaction --exit-on-error -d "$S_DB" )
SECS=$(( $(date +%s) - START ))

( standby_env; counts_sql | psql -X -q -A -t -v ON_ERROR_STOP=1 ) > "$TMP/actual"
diff -u "$TMP/expected" "$TMP/actual" >&2 || die "Neon 대기 복원본이 덤프 시점과 다르다(위 diff)"
TABLES=$(grep -vc '^flyway|' "$TMP/actual" || true)
health standby "secs=$SECS" "tables=$TABLES"
log "✅ Neon 대기 복원 판정 통과 — ${SECS}s, 테이블 ${TABLES}개, Flyway 이력 일치"
