#!/usr/bin/env bash
# prod DB(EC2 Postgres) 운영 스크립트 공용 — 📌D-016 순서표 6-A. backup·restore-check·neon-import 가 source 한다.
#
# 🔴 이 디렉터리는 **prod 전용**이다. 학습 클러스터용 `../db-backup.sh`·`../db-restore.sh` 와 섞지 마라.
#    그쪽은 *"prod Neon 에 붙을 수 없다"* 를 **호스트 인자가 없다는 사실**로 구현했다(N-17).
#    거기에 호스트 인자를 더하는 대신 여기를 따로 만들었다(B6-4). S3 prefix 도 다르다(B6-6).
#
# 설정은 `/etc/devquest-db.env`(install.sh 가 쓴다): REGION · BUCKET · PREFIX · DB · CUTOVER_DONE · SSM_PREFIX.
# 비밀은 파일에 없다 — Neon 접속 정보는 실행 때 SSM 에서 받고 환경변수로만 넘긴다(argv 에 두면 ps 에 보인다).
set -euo pipefail
export PATH="$PATH:/snap/bin:/usr/lib/postgresql/17/bin"
# runuser 로 postgres 가 된 psql 이 cwd(예: SSM 세션의 /root)에 접근 못 하면 경고를 낸다 — 아예 / 에서 돈다(QA F-5).
cd /

ENV_FILE="${DEVQUEST_DB_ENV:-/etc/devquest-db.env}"
[ -f "$ENV_FILE" ] || { echo "[prod-db] 설정 없음: $ENV_FILE — install.sh 를 먼저" >&2; exit 2; }
# shellcheck disable=SC1090
. "$ENV_FILE"
: "${REGION:?}" "${BUCKET:?}" "${PREFIX:?}" "${DB:?}" "${CUTOVER_DONE:?}" "${SSM_PREFIX:?}"

# 🔴 stdout 은 **덤프 바이트 전용**인 함수가 있다(snapshot_dump). 로그는 전부 stderr.
log() { echo "[prod-db $(date -u +%H:%M:%SZ)] $*" >&2; }
die() { log "❌ $*"; exit 1; }

# root 에서 postgres OS 사용자로 — 로컬 peer 인증. `sudo` 대신 runuser(util-linux): 비대화형, 패키지 의존 없음.
AS_PG=(runuser -u postgres --)

stamp() { date -u +%Y%m%dT%H%M%SZ; }

# ── 데이터 판정용 행 수 ───────────────────────────────────────────────
# 출력: `<schema>.<table>|<행 수>` 정렬 + 마지막 줄 `flyway|<이력 지문>`.
# 🔑 `app.jar validate` 를 쓰지 않는 이유(B6-3): VALIDATE 모드가 `*:pending` 을 무시해 **빈 DB 도 exit 0** 이다.
#    부재가 성공처럼 보이는 검사다. 여기선 테이블별 행 수와 Flyway 이력 전체를 **값으로** 비교한다.
# SELECT 권한이 없는 테이블은 건너뛴다 — 그런 테이블은 pg_dump 가 먼저 permission denied 로 시끄럽게 죽는다.
counts_sql() {
  cat <<'SQL'
SELECT n.nspname || '.' || c.relname || '|' ||
       (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I', n.nspname, c.relname), false, true, '')))[1]::text
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind IN ('r', 'p')
   AND n.nspname NOT IN ('pg_catalog', 'information_schema') AND n.nspname NOT LIKE 'pg\_toast%'
   AND c.relpersistence <> 't'  -- 다른 세션의 임시 테이블은 count 가 에러다(QA F-4)
   AND has_table_privilege(c.oid, 'SELECT')
 ORDER BY (n.nspname || '.' || c.relname) COLLATE "C";  -- 서버 collation 과 무관한 순서(Neon↔EC2 비교, F-4)
SELECT 'flyway|' || CASE WHEN to_regclass('flyway_schema_history') IS NULL THEN 'absent'
  ELSE coalesce((xpath('/row/v/text()', query_to_xml(
         'SELECT string_agg(coalesce(version, ''R'') || '':'' || coalesce(checksum, 0) || '':'' || success, '','' ORDER BY installed_rank) AS v FROM flyway_schema_history',
         false, true, '')))[1]::text, 'empty') END;
SQL
}

# ── 시퀀스 판정(📌D-016 7-A, B7-24) — 행 수·Flyway 만 보면 setval 이 빠지거나 틀려도 "일치" 다.
#    그러면 전환 직후 첫 INSERT 가 기존 id 와 충돌한다(duplicate key). 원천과 값 비교는 하지 않는다 — 시퀀스는
#    트랜잭션 밖이라 스냅샷과 어긋난다. 대신 **복원본 자체의 성질**을 본다: 소유 시퀀스의 last_value ≥ 그 컬럼 max.
#    출력이 비어 있으면 통과. 한 줄이라도 있으면 그 시퀀스가 뒤처졌다.
seq_sql() {
  cat <<'SQL'
SELECT n.nspname || '.' || c.relname || ' last=' || coalesce(s.last_value, 0) || ' max=' || m.mx
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_depend d ON d.objid = c.oid AND d.classid = 'pg_class'::regclass AND d.refclassid = 'pg_class'::regclass AND d.deptype IN ('a', 'i')
  JOIN pg_class t ON t.oid = d.refobjid
  JOIN pg_namespace tn ON tn.oid = t.relnamespace
  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
  JOIN pg_sequences s ON s.schemaname = n.nspname AND s.sequencename = c.relname
  CROSS JOIN LATERAL (SELECT coalesce((xpath('/row/m/text()', query_to_xml(
          format('SELECT max(%I)::bigint AS m FROM %I.%I', a.attname, tn.nspname, t.relname), false, true, '')))[1]::text::bigint, 0) AS mx) m
 WHERE c.relkind = 'S' AND coalesce(s.last_value, 0) < m.mx
 ORDER BY 1;
SQL
}
local_seq_check() {  # $1 = DB 이름 — 위반 줄을 출력, 있으면 rc 1
  # ⚠️ 호출부가 `local_seq_check … || die` 라 set -e 가 꺼진다 — 조회 실패를 빈 출력(= 통과)으로 읽지 않게 rc 를 직접 본다.
  local out; out=$(seq_sql | "${AS_PG[@]}" psql -X -q -A -t -v ON_ERROR_STOP=1 -d "$1") || { echo "시퀀스 판정 쿼리 실패" >&2; return 2; }
  [ -z "$out" ] || { printf '%s\n' "$out" >&2; return 1; }
}

# snapshot_dump <행 수 출력 파일> — **같은 스냅샷**에서 `-Fc` 덤프(stdout)와 행 수(파일)를 만든다.
#   호출 전에 PSQL_PREFIX(배열)·PSQL_CONN(배열)을 정한다. 로컬: (runuser…) + (-d devquest), 원격: () + () (PG* 환경변수).
# 왜 스냅샷인가: 덤프와 행 수를 따로 세면 그 사이 쓰기 때문에 **정상 백업도 불일치**로 판정된다.
#   REPEATABLE READ 세션에서 `pg_export_snapshot()` → `pg_dump --snapshot` 으로 같은 시점을 본다.
#   ⚠️ Neon 은 **직접 호스트**여야 한다(pooler 는 세션이 다른 백엔드로 갈 수 있어 스냅샷을 못 찾는다).
# shellcheck disable=SC2034  # PSQL_PREFIX·PSQL_CONN 은 호출부가 정하는 입력이다
snapshot_dump() {
  local out="$1" snap line=""
  # stderr 는 섞지 않는다(QA F-5) — 경고 한 줄이 스냅샷 id·행 수 자리로 읽힌다. 에러면 EOF → 아래 die.
  coproc SNAPPG { "${PSQL_PREFIX[@]}" psql -X -q -A -t -v ON_ERROR_STOP=1 "${PSQL_CONN[@]}"; }
  local w="${SNAPPG[1]}" r="${SNAPPG[0]}"
  printf '%s\n' "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;" "SELECT pg_export_snapshot();" >&"$w"
  read -r -t 60 snap <&"$r" || die "스냅샷 내보내기 응답 없음"
  [[ "$snap" =~ ^[0-9A-F]+-[0-9A-F]+-[0-9]+$ ]] || die "스냅샷 id 형식 이상: $snap"
  "${PSQL_PREFIX[@]}" pg_dump -Fc --no-owner --no-acl --snapshot="$snap" "${PSQL_CONN[@]}"
  { counts_sql; echo "SELECT '__END__';"; } >&"$w"
  : > "$out"
  while read -r -t 300 line <&"$r"; do
    [ "$line" = "__END__" ] && break
    printf '%s\n' "$line" >> "$out"
  done
  [ "$line" = "__END__" ] || die "행 수 수집 실패(마지막 줄: ${line:-없음})"
  printf 'COMMIT;\n\\q\n' >&"$w"
  wait "$SNAPPG_PID" 2>/dev/null || true
}

# 로컬 DB 의 행 수 (스냅샷 불필요 — 복원 직후 아무도 안 쓴다)
local_counts() {  # $1 = DB 이름
  counts_sql | "${AS_PG[@]}" psql -X -q -A -t -v ON_ERROR_STOP=1 -d "$1"
}

# 사용자 테이블 수 (빈 DB 판정)
user_table_count() {  # $1 = DB 이름
  "${AS_PG[@]}" psql -X -q -A -t -v ON_ERROR_STOP=1 -d "$1" -c \
    "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE c.relkind IN ('r','p') AND n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg\_toast%'"
}

# 🔴 전환 완료 표식은 **데이터 볼륨**에도 둔다(QA F-2). /etc 는 루트 볼륨이라 인스턴스를 교체하면 사라지고,
#    인자 없이 재설치하면 CUTOVER_DONE=0 으로 가드가 열린다. 데이터 볼륨은 인스턴스보다 오래 산다(5-A).
CUTOVER_MARK=/var/lib/postgresql/.devquest-cutover-done
cutover_done() { [ "$CUTOVER_DONE" != "0" ] || [ -e "$CUTOVER_MARK" ]; }

disk_pct() { df --output=pcent /var/lib/postgresql | tail -1 | tr -dc '0-9'; }

# 헬스 키 — 본문 없는 객체. 감시(6-D)는 **ListObjectsV2 만으로** 읽는다(좁힌 읽기 전용 역할은 GetObject Deny, B6-31).
# 그래서 값은 키 이름에 넣는다: health/<kind>/<stamp>_<k=v>_<k=v>
health() {  # $1 = kind, $2.. = k=v
  local kind="$1"; shift
  local key
  key="${PREFIX}health/$kind/$(stamp)"
  local kv; for kv in "$@"; do key="${key}_$kv"; done
  aws s3api put-object --region "$REGION" --bucket "$BUCKET" --key "$key" >/dev/null
  log "헬스 키: $key"
}
