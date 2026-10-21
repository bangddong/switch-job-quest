#!/usr/bin/env bash
# Neon 콜드 대기 복원 — 📌D-016 Q-2. 최신 S3 덤프(`.counts` 짝)를 **Neon 대기 DB** 에 덮어쓰고 값으로 판정한다.
#
#   6-C(2026-10-20): Neon **브랜치** `standby-rehearsal` 에 손으로 1회 — 도구 리허설.
#   7번 전환 뒤: 매일 timer 로(옛 prod Neon 이 계정 밖 사본이 된다). 그 배선은 7번 범위.
#
# 🔴 prod Neon(기본 브랜치 — 이 프로젝트에선 `production`)을 덮어쓰지 않는 것이 이 스크립트의 전부다(B6-1). 가드:
#    ① role 이름 `devquest_standby` + 접속해서 `neon_superuser` **상속까지** 비소속·superuser 계열 속성 없음
#       (브랜치에서 **SQL 로** 만든 role 만 — 콘솔 role 은 neon_superuser 자동 소속, 6-0 실측)
#    ② 대기 엔드포인트 ≠ prod 엔드포인트(소문자·끝 점·`-pooler` 정규화) · 대기 DB ≠ prod DB
#    ③ **격리 직접 증명** — 대기 자격증명으로 prod 엔드포인트에 붙어 **거부돼야** 진행(접속되면 = role 이 prod 에도 있다).
#       거부 사유가 인증·role 부재·hba 일 때만 통과, DNS·타임아웃 같은 모호한 실패는 중단
#    ④ prod 좌표는 SSM `neon-source-url` 과 `NEON_PROD_HOST`·`NEON_PROD_DB` 를 **모두** 모아 대조. **하나도 없으면 덮어쓰지 않는다**
#    적재: 덤프를 SQL 로 **끝까지 변환한 뒤** public 스키마를 한 트랜잭션으로 통째 교체(실패 시 이전 복원본 보존).
#    ⚠️ 7번 전환 뒤 대기 = 옛 prod 가 되면 ②③이 항상 막는다 — 가드 재정의는 7번(원장 L-76).
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

# prod 좌표 — SSM 과 환경변수 **둘 다** 모아 전부와 대조한다(QA F-4: 한쪽이 다른 쪽을 가리면 낡은 값 하나와만 비교된다).
P_HOSTS=(); P_DBS=()
[ -n "${NEON_PROD_HOST:-}" ] && P_HOSTS+=("$NEON_PROD_HOST")
[ -n "${NEON_PROD_DB:-}" ] && P_DBS+=("$NEON_PROD_DB")
if SRC=$(get_url neon-source-url) && [[ "$SRC" =~ $re ]]; then P_HOSTS+=("${BASH_REMATCH[4]}"); P_DBS+=("${BASH_REMATCH[7]}"); fi
unset SRC
# 전환(7번) 뒤 `neon-source-url`(읽기 전용 비밀)을 지워도 좌표는 남긴다 — 비밀이 아닌 `neon-prod-coords`(String, "호스트/DB").
#   이것이 없으면 가드 ④가 die 해 대기 복원이 영구히 멈춘다(B7-8). Neon production 브랜치는 전환 뒤엔 **동결된 사본**이고
#   여전히 덮어쓰면 안 된다 — 대기는 **전용 브랜치**라 "대기 ≠ production" 모델이 그대로 유효하다(L-76 해소).
if COORDS=$(get_url neon-prod-coords) && [[ "$COORDS" =~ ^([^/[:space:]]+)/([^/[:space:]]+)$ ]]; then
  P_HOSTS+=("${BASH_REMATCH[1]}"); P_DBS+=("${BASH_REMATCH[2]}")
fi
[ "${#P_HOSTS[@]}" -gt 0 ] && [ "${#P_DBS[@]}" -gt 0 ] \
  || die "prod Neon 좌표를 모른다 — neon-source-url 도 NEON_PROD_HOST·NEON_PROD_DB 도 없다. 대조 없이 덮어쓰지 않는다"

# Neon 호스트 정규화 → 엔드포인트 ID(첫 라벨, 소문자, `-pooler` 제거, 끝 점 제거). 같은 엔드포인트의
# 직접/pooler·대소문자 변형을 같은 것으로 본다(QA F-1a). 예: EP-ABC-pooler.x.neon.tech. → ep-abc
endpoint_id() { local h; h=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]'); h="${h%.}"; h="${h%%.*}"; echo "${h%-pooler}"; }

[ "$S_USER" = "$STANDBY_USER" ] || die "대기 role 이 $STANDBY_USER 가 아니다($S_USER) — 브랜치 전용 role 만 쓴다"
[[ "$S_HOST" != *-pooler* ]] || die "pooler 호스트다 — 직접 호스트를 쓴다"
for ph in "${P_HOSTS[@]}"; do
  [ "$(endpoint_id "$S_HOST")" != "$(endpoint_id "$ph")" ] || die "대기 엔드포인트가 prod 와 같다($(endpoint_id "$S_HOST")) — prod 를 덮어쓸 수 있다. 중단"
done
for pd in "${P_DBS[@]}"; do [ "$S_DB" != "$pd" ] || die "대기 DB 이름이 prod DB 이름과 같다($S_DB) — 중단"; done

standby_env() {  # 서브셸 안에서만
  export PGHOST="$S_HOST" PGPORT="$S_PORT" PGUSER="$S_USER" PGDATABASE="$S_DB" PGSSLMODE=require
  PGPASSWORD="$(printf '%b' "${S_PASS//%/\\x}")"; export PGPASSWORD
}

# 접속해서 role·DB·권한을 본다. neon_superuser 는 **상속까지**(pg_has_role MEMBER), 위험 속성도(QA F-5).
CHK=$( standby_env; psql -X -q -A -t -v ON_ERROR_STOP=1 -c \
  "SELECT current_user || '|' || current_database() || '|' ||
          (CASE WHEN to_regrole('neon_superuser') IS NULL THEN false ELSE pg_has_role(current_user, 'neon_superuser', 'MEMBER') END)::text || '|' ||
          (SELECT (rolsuper OR rolcreaterole OR rolbypassrls)::text FROM pg_roles WHERE rolname = current_user)" ) \
  || die "Neon 대기 DB 접속 실패(호스트·비번·네트워크)"
[ "$CHK" = "$STANDBY_USER|$S_DB|false|false" ] || die "대기 접속 확인 실패(${CHK%%|*}…) — role·DB 가 다르거나 neon_superuser·superuser 계열이다"

# 🔴 **격리를 직접 증명한다**(QA F-1b) — 대기 자격증명으로 **prod 엔드포인트**에 붙어 본다. **실패해야 통과.**
#    브랜치에서 SQL 로 만든 role 은 그 브랜치에만 있으므로 main 엔 없다. TASK-15 에서 SQL Editor 의 브랜치를 안 바꾸고
#    main 에 만들었다면 여기서 접속이 **된다** → 이 자격증명은 prod 에 쓸 수 있다 → 중단.
#    거부 사유가 인증·role 부재·hba 거부일 때만 통과로 친다. 타임아웃·DNS 등 모호한 실패는 **중단**(fail-closed).
for i in "${!P_HOSTS[@]}"; do
  ph="${P_HOSTS[$i]}"; pd="${P_DBS[$i]:-${P_DBS[0]}}"
  if ERR=$( standby_env; export PGHOST="$ph" PGDATABASE="$pd" PGCONNECT_TIMEOUT=10; psql -X -q -A -t -c 'SELECT 1' 2>&1 ); then
    die "대기 자격증명으로 prod 엔드포인트($(endpoint_id "$ph"))/$pd 에 **접속된다** — 이 role 은 prod 에도 있다(main 에 만들었나?). 중단"
  fi
  echo "$ERR" | grep -qiE 'password authentication failed|role .* does not exist|rejects connection|not allowed|no pg_hba.conf entry' \
    || die "prod 엔드포인트 접속 거부 사유가 모호하다 — 격리를 증명하지 못했다: $(echo "$ERR" | head -1 | cut -c1-120)"
done
log "Neon 대기 접속 확인 — $(endpoint_id "$S_HOST")/$S_DB · prod 엔드포인트는 이 자격증명을 거부(격리 확인)"

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
CKEY=$(aws s3api list-objects-v2 --region "$REGION" --bucket "$BUCKET" --prefix "${PREFIX}dumps/" \
        --query "sort_by(Contents[?ends_with(Key, '.counts')], &Key)[-1].Key" --output text)
[[ "$CKEY" == "${PREFIX}dumps/devquest-"*.counts ]] || die "완결된 덤프(.counts 짝)가 없다(${CKEY:-없음})"
aws s3 cp --region "$REGION" --only-show-errors "s3://$BUCKET/$CKEY" "$TMP/expected"
log "복원 대상: ${CKEY%.counts}.dump → Neon 대기"

START=$(date +%s)
# public 스키마를 통째로 비우고 덤프를 다시 넣는다 — **한 트랜잭션**(psql -1): 실패하면 이전 복원본이 그대로 남는다.
#   `pg_restore --clean` 은 덤프에 **있는** 객체만 지운다 → 나중 마이그레이션이 DROP 한 테이블이 대기 DB 에 남아
#   판정 diff 가 영구 실패한다(QA F-2). 그래서 스키마째 교체한다(대기 DB·public 소유자가 이 role).
aws s3 cp --region "$REGION" --only-show-errors "s3://$BUCKET/${CKEY%.counts}.dump" "$TMP/d.dump"
# 🔴 덤프를 **먼저 SQL 파일로 끝까지 변환**한다(QA F-9). 스트리밍(`pg_restore -f - | psql -1`)이면 덤프 중간이 깨졌을 때
#    psql 이 EOF 뒤 COMMIT 을 보내 *DROP SCHEMA + 반쪽 복원* 이 이전 복원본을 대체할 수 있다. `-Fc` 는 목차가 앞에 있어
#    `pg_restore -l` 만으로는 꼬리·데이터 블록 손상을 못 잡는다. 변환이 끝까지 성공해야만 대기 DB 를 건드린다.
pg_restore --no-owner --no-acl -f "$TMP/restore.sql" "$TMP/d.dump" \
  || die "덤프를 SQL 로 변환하지 못했다(손상·잘림) — 대기 DB 는 건드리지 않았다"
{ echo "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"; cat "$TMP/restore.sql"; } \
  | ( standby_env; psql -X -q -1 -v ON_ERROR_STOP=1 >/dev/null )
SECS=$(( $(date +%s) - START ))

( standby_env; counts_sql | psql -X -q -A -t -v ON_ERROR_STOP=1 ) > "$TMP/actual"
diff -u "$TMP/expected" "$TMP/actual" >&2 || die "Neon 대기 복원본이 덤프 시점과 다르다(위 diff)"
SEQ=$( standby_env; seq_sql | psql -X -q -A -t -v ON_ERROR_STOP=1 ) || die "대기 시퀀스 판정 쿼리 실패"
[ -z "$SEQ" ] || { printf '%s\n' "$SEQ" >&2; die "대기 복원본의 시퀀스가 데이터보다 뒤처졌다(위) — 복귀하면 첫 INSERT 가 id 충돌한다"; }
TABLES=$(grep -vc '^flyway|' "$TMP/actual" || true)
health standby "secs=$SECS" "tables=$TABLES"
log "✅ Neon 대기 복원 판정 통과 — ${SECS}s, 테이블 ${TABLES}개, Flyway 이력 일치"
