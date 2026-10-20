#!/usr/bin/env bash
# scripts/prod-db 회귀 테스트 — **진짜 Postgres 17**(docker) + 목 aws. AWS 접촉 0건, $0. 📌D-016 6-A.
#
# 왜 목 psql 이 아니라 진짜 Postgres 인가: 이 스크립트들의 핵심 주장은 *"덤프와 행 수가 같은 스냅샷"* ·
#   *"복원본이 원본과 값으로 같다"* · *"Neon 에 쓰지 않는다"* 다. 목으로는 셋 다 **흉내만** 낼 수 있다
#   (CLAUDE.md 「검사가 주장보다 헐겁다」). 그래서 실제 pg_dump·pg_restore·스냅샷을 돌린다.
#   "Neon" 은 같은 컨테이너의 다른 DB(neonsrc) + 읽기 전용 role 로 흉내 낸다 — TCP·TLS 경유.
#
# 사용: bash infra/aws-eks/scripts/prod-db/tests/prod-db-test.sh      (docker 필요)
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
C="dq-prod-db-test-$$"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "  ✅ $1"; }
bad() { FAIL=$((FAIL+1)); echo "  🔴 $1"; }
X()   { docker exec -i "$C" bash -c "$1"; }          # 컨테이너 안 root
Q()   { docker exec -i "$C" runuser -u postgres -- psql -X -q -A -t -d "$1" -c "$2"; }
trap 'docker rm -f "$C" >/dev/null 2>&1' EXIT

echo "── 준비: postgres:17 (TLS 켬 — neon-import 는 sslmode=require)"
docker run -d --name "$C" -e POSTGRES_PASSWORD=unused postgres:17 \
  -c ssl=on -c ssl_cert_file=/etc/ssl/certs/ssl-cert-snakeoil.pem -c ssl_key_file=/etc/ssl/private/ssl-cert-snakeoil.key >/dev/null
for _ in $(seq 1 60); do docker exec "$C" pg_isready -q -h localhost 2>/dev/null && break; perl -e 'select undef,undef,undef,0.5'; done
docker exec "$C" pg_isready -q -h localhost || { echo "postgres 기동 실패"; docker logs "$C" | tail -5; exit 1; }

docker exec "$C" mkdir -p /opt/src
docker cp "$HERE/." "$C:/opt/src/"
# 목 aws — s3 는 /mock-s3/<버킷>/<키> 파일, ssm 은 /mock-ssm/<이름>. 실제 CLI 의 인자 모양만 받는다.
X 'cat > /usr/local/bin/aws <<"EOF"
#!/usr/bin/env bash
set -euo pipefail
args=(); while [ $# -gt 0 ]; do case "$1" in --region|--output) shift 2;; --only-show-errors|--with-decryption) shift;; *) args+=("$1"); shift;; esac; done
set -- "${args[@]}"
path() { local u="${1#s3://}"; echo "/mock-s3/$u"; }
case "$1 $2" in
  "s3 cp")
    if [ "$3" = "-" ]; then f=$(path "$4"); mkdir -p "$(dirname "$f")"; cat > "$f"
    elif [ "$4" = "-" ]; then cat "$(path "$3")"
    elif [[ "$3" == s3://* ]]; then cp "$(path "$3")" "$4"
    else f=$(path "$4"); mkdir -p "$(dirname "$f")"; cp "$3" "$f"; fi ;;
  "s3api head-object") b=$4; k=$6; stat -c %s "/mock-s3/$b/$k" ;;
  "s3api put-object")  b=$4; k=$6; mkdir -p "$(dirname "/mock-s3/$b/$k")"; : > "/mock-s3/$b/$k" ;;
  "s3api list-objects-v2") b=$4; p=$6
    # 목 자신이 pipefail 이라 없는 경로의 ls 실패가 "조회 실패"(rc 2)로 새지 않게 || true — 실제 S3 는 빈 목록을 돌려준다
    if [[ "$p" == */health/* ]]; then k=$( (ls "/mock-s3/$b/$p" 2>/dev/null || true) | sort | tail -1)   # watch.sh — 최신 헬스 키
    else case "$8" in *.counts*) sfx=counts;; *) sfx=dump;; esac
         k=$(ls "/mock-s3/$b/$p" 2>/dev/null | grep "\.$sfx$" | sort | tail -1); fi
    if [ -n "$k" ]; then echo "$p$k"; else echo None; fi ;;
  "ssm get-parameter") cat "/mock-ssm/$4" ;;
  *) echo "mock aws: 모르는 호출 $*" >&2; exit 9 ;;
esac
EOF
chmod +x /usr/local/bin/aws
mkdir -p /mock-ssm/devquest/prod/db
cat > /etc/devquest-db.env <<EOF
REGION=ap-northeast-2
BUCKET=bkt
PREFIX=prod-db/
SSM_PREFIX=/devquest/prod/db
DB=devquest
CUTOVER_DONE=0
EOF'
# user_data 가 만드는 것(롤·DB) + "Neon"(neonsrc: Flyway 이력 + 앱 테이블 + 읽기 전용 role)
# CREATE DATABASE 는 트랜잭션 블록 안에서 못 돈다 — `-c` 하나에 묶으면 암묵 트랜잭션이라 실패한다.
for s in "CREATE ROLE devquest LOGIN PASSWORD 'x'" "CREATE DATABASE devquest OWNER devquest" "CREATE DATABASE neonsrc"; do
  Q postgres "$s" >/dev/null || { echo "준비 실패: $s"; exit 1; }
done
Q neonsrc "
CREATE TABLE flyway_schema_history(installed_rank int PRIMARY KEY, version varchar(50), description varchar(200), type varchar(20), script varchar(1000), checksum int, installed_by varchar(100), installed_on timestamp DEFAULT now(), execution_time int, success boolean);
INSERT INTO flyway_schema_history VALUES (1,'1','init','SQL','V1__init.sql',111,'x',now(),1,true),(2,'2','quests','SQL','V2__q.sql',-222,'x',now(),1,true);
CREATE TABLE users(id serial PRIMARY KEY, name text NOT NULL);
CREATE TABLE quest_progress(id bigserial PRIMARY KEY, user_id int REFERENCES users(id), xp int);
INSERT INTO users(name) SELECT 'u'||g FROM generate_series(1,50) g;
INSERT INTO quest_progress(user_id,xp) SELECT 1+(g%50), g FROM generate_series(1,500) g;
CREATE ROLE devquest_ro LOGIN PASSWORD 'ro_pw';
GRANT USAGE ON SCHEMA public TO devquest_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO devquest_ro;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO devquest_ro;" >/dev/null
setp() { X "printf '%s' '$1' > /mock-ssm/devquest/prod/db/neon-source-url"; }
setp 'postgresql://devquest_ro:ro_pw@localhost:5432/neonsrc?sslmode=require'
RUN() { docker exec -i "$C" bash "/opt/src/$1" ${2:-} 2>&1; }

echo "── ① 빈 DB 백업(7번 전 상태) — flyway absent 로 정상 종료"
out=$(RUN backup.sh); rc=$?
[ $rc -eq 0 ] && ok "rc=0" || { bad "rc=$rc"; echo "$out" | tail -5; }
X 'cat /mock-s3/bkt/prod-db/dumps/*.counts' | grep -qx 'flyway|absent' && ok "counts: flyway|absent" || bad "flyway 줄 이상"
[ "$(X 'ls /mock-s3/bkt/prod-db/health/backup | wc -l')" = 1 ] && ok "헬스 키 1개" || bad "헬스 키 없음"
X 'ls /mock-s3/bkt/prod-db/health/backup' | grep -qE '_disk=[0-9]+_bytes=[0-9]+_tables=0$' && ok "헬스 키 형식" || bad "헬스 키 형식 이상: $(X 'ls /mock-s3/bkt/prod-db/health/backup')"

echo "── ② neon-import — Neon 스냅샷과 값으로 같다, 소유자 devquest"
out=$(RUN neon-import.sh); rc=$?
[ $rc -eq 0 ] && ok "rc=0" || { bad "rc=$rc"; echo "$out" | tail -8; }
[ "$(Q devquest 'SELECT count(*) FROM quest_progress')" = 500 ] && ok "행 이전(500)" || bad "행 수 이상"
[ "$(Q devquest "SELECT tableowner FROM pg_tables WHERE tablename='users'")" = devquest ] && ok "소유자 devquest(Flyway 가 이어서 migrate 가능)" || bad "소유자 이상"
echo "$out" | grep -q 'ro_pw' && bad "🔴 비밀번호가 출력에 나왔다" || ok "출력에 비밀번호 없음"

echo "── ③ 대상이 비어 있지 않으면 거부, --reset 이면 다시"
out=$(RUN neon-import.sh); rc=$?
[ $rc -ne 0 ] && echo "$out" | grep -q '비어 있지 않다' && ok "거부" || bad "비어 있지 않은데 덮어썼다(rc=$rc)"
out=$(RUN neon-import.sh --reset); rc=$?
[ $rc -eq 0 ] && [ "$(Q devquest 'SELECT count(*) FROM users')" = 50 ] && ok "--reset 재가져오기" || { bad "--reset 실패 rc=$rc"; echo "$out" | tail -5; }

echo "── ④ 쓰기 권한이 있는 role 이면 거부 (B6-12)"
Q neonsrc "GRANT INSERT ON users TO devquest_ro" >/dev/null
out=$(RUN neon-import.sh --reset); rc=$?
[ $rc -ne 0 ] && echo "$out" | grep -q '읽기 전용 role 이 아니다' && ok "거부" || bad "🔴 쓰기 role 로 진행했다(rc=$rc)"
Q neonsrc "REVOKE INSERT ON users FROM devquest_ro" >/dev/null
[ "$(Q devquest 'SELECT count(*) FROM users')" = 50 ] && ok "거부 전에 대상 DB 를 지우지 않았다" || bad "🔴 거부했는데 --reset 이 대상을 이미 지웠다"

echo "── ⑤ role 이름이 devquest_ro 가 아니면 거부"
setp 'postgresql://devquest:x@localhost:5432/neonsrc?sslmode=require'
out=$(RUN neon-import.sh --reset); rc=$?
[ $rc -ne 0 ] && echo "$out" | grep -q '읽기 전용 role 만' && ok "거부" || bad "다른 role 로 진행(rc=$rc)"
setp 'postgresql://devquest_ro:ro_pw@ep-x-pooler.neon.tech/neonsrc?sslmode=require'
out=$(RUN neon-import.sh --reset); rc=$?
[ $rc -ne 0 ] && echo "$out" | grep -q 'pooler' && ok "pooler 호스트 거부" || bad "pooler 로 진행(rc=$rc)"
setp 'postgresql://devquest_ro:ro_pw@localhost:5432/neonsrc?sslmode=require'

echo "── ⑥ 전환 완료(CUTOVER_DONE=1)면 어떤 경우에도 거부"
X "sed -i 's/^CUTOVER_DONE=0/CUTOVER_DONE=1/' /etc/devquest-db.env"
out=$(RUN neon-import.sh --reset); rc=$?
[ $rc -ne 0 ] && echo "$out" | grep -q '가져오기 금지' && ok "거부" || bad "🔴 전환 뒤 가져오기 진행(rc=$rc)"
X "sed -i 's/^CUTOVER_DONE=1/CUTOVER_DONE=0/' /etc/devquest-db.env"

echo "── ⑥b 데이터 볼륨 표식만 있어도 거부 (QA F-2 — 인스턴스 교체로 /etc 가 사라진 경우)"
X 'touch /var/lib/postgresql/.devquest-cutover-done'
out=$(RUN neon-import.sh --reset); rc=$?
[ $rc -ne 0 ] && echo "$out" | grep -q '데이터 볼륨에 있다' && ok "거부(CUTOVER_DONE=0 이어도)" || bad "🔴 표식을 무시했다(rc=$rc)"
X 'rm -f /var/lib/postgresql/.devquest-cutover-done'

echo "── ⑥c 앱이 붙어 있으면 --reset 거부 (QA F-2 세 번째 가드)"
X "PGPASSWORD=x psql -X -q -h localhost -U devquest -d devquest -c 'SELECT pg_sleep(6)' >/dev/null 2>&1" &
L=$!; perl -e 'select undef,undef,undef,1.0'
out=$(RUN neon-import.sh --reset); rc=$?
wait $L
[ $rc -ne 0 ] && echo "$out" | grep -q '원격 접속' && ok "거부" || bad "🔴 접속 중인 DB 를 지웠다(rc=$rc)"
[ "$(Q devquest 'SELECT count(*) FROM users')" = 50 ] && ok "대상 보존" || bad "대상이 지워졌다"

echo "── ⑥d 비밀번호에 @ : % 가 있어도(URL 인코딩) 가져온다"
Q neonsrc "ALTER ROLE devquest_ro PASSWORD 'p@ss:w%rd'" >/dev/null
setp 'postgresql://devquest_ro:p%40ss%3Aw%25rd@localhost:5432/neonsrc?sslmode=require'
out=$(RUN neon-import.sh --reset); rc=$?
[ $rc -eq 0 ] && ok "인코딩 비번 디코딩" || { bad "rc=$rc"; echo "$out" | tail -4; }
echo "$out" | grep -q 'p@ss\|p%40ss' && bad "🔴 비밀번호가 출력에 나왔다" || ok "출력에 비밀번호 없음"

echo "── ⑦ 쓰기가 계속되는 중에 백업 → 복원 판정 통과 (스냅샷 일관성)"
echo "   (무엇의 실패를 잡나: 덤프와 행 수를 다른 시점에 세면 정상 백업이 불일치로 판정된다)"
X "for i in \$(seq 1 400); do runuser -u postgres -- psql -X -q -d devquest -c 'INSERT INTO quest_progress(user_id,xp) VALUES (1,1)' >/dev/null; done" &
W=$!
perl -e 'select undef,undef,undef,0.3'
out=$(RUN backup.sh); rc=$?
wait $W
[ $rc -eq 0 ] && ok "백업 rc=0" || { bad "백업 rc=$rc"; echo "$out" | tail -5; }
out=$(RUN restore-check.sh); rc=$?
[ $rc -eq 0 ] && ok "복원 판정 통과" || { bad "복원 판정 실패 rc=$rc"; echo "$out" | tail -12; }
[ -z "$(Q postgres "SELECT 1 FROM pg_database WHERE datname='devquest_restore_check'")" ] && ok "스크래치 DB 삭제됨" || bad "스크래치 DB 남음"
X 'ls /mock-s3/bkt/prod-db/health/restore' | grep -qE '_secs=[0-9]+_tables=3$' && ok "restore 헬스 키(secs·tables)" || bad "restore 헬스 키 이상"

echo "── ⑦b 백업이 중간에 죽어 잘린 .dump 만 남아도 restore-check 는 짝(.counts)이 있는 최신을 고른다 (QA F-1)"
X ': > /mock-s3/bkt/prod-db/dumps/devquest-29991231T000000Z.dump'
out=$(RUN restore-check.sh); rc=$?
[ $rc -eq 0 ] && ok "잘린 덤프를 건너뛰고 통과" || { bad "잘린 덤프를 골랐다(rc=$rc)"; echo "$out" | tail -3; }
X 'rm -f /mock-s3/bkt/prod-db/dumps/devquest-29991231T000000Z.dump'

echo "── ⑧ 반증: 행 수 파일을 바꾸면 복원 판정이 실패해야 한다 + 실패해도 스크래치 삭제"
X 'f=$(ls /mock-s3/bkt/prod-db/dumps/*.counts | sort | tail -1); sed -i "s/^public.users|50$/public.users|49/" "$f"; grep -q "users|49" "$f"' \
  || bad "반증 준비 실패 — 바꿀 줄을 못 찾았다"
out=$(RUN restore-check.sh); rc=$?
[ $rc -ne 0 ] && echo "$out" | grep -q '복원본이 덤프 시점과 다르다' && ok "불일치 검출" || bad "🔴 바뀐 행 수를 통과시켰다 — 판정이 공허하다(rc=$rc)"
[ -z "$(Q postgres "SELECT 1 FROM pg_database WHERE datname='devquest_restore_check'")" ] && ok "실패해도 스크래치 삭제" || bad "실패 경로에서 스크래치 남음"

echo "── ⑨ 반증: Flyway 이력 체크섬이 다르면 실패"
X 'f=$(ls /mock-s3/bkt/prod-db/dumps/*.counts | sort | tail -1); sed -i "s/^public.users|49$/public.users|50/; s/:-222:/:-223:/" "$f"; grep -q ":-223:" "$f"' || bad "반증 준비 실패"
out=$(RUN restore-check.sh); rc=$?
[ $rc -ne 0 ] && ok "체크섬 불일치 검출" || bad "🔴 체크섬 변조 통과"

echo "── ⑪ watch.sh — 헬스 키 이름만으로 판정 (QA 6-D F-4: 유일한 알림 경로의 회귀)"
WN() { docker exec -i -e BUCKET=bkt "$@" "$C" bash /opt/src/watch.sh 2>&1; }
NOW=$(date -u +%s)
out=$(WN); rc=$?
[ $rc -eq 0 ] && ok "정상 → 통과" || { bad "정상인데 실패 rc=$rc"; echo "$out" | tail -4; }
out=$(WN -e WATCH_NOW=$((NOW+30*3600))); rc=$?
[ $rc -eq 1 ] && echo "$out" | grep -q 'backup 이 3[0-9]h 전이 마지막' && ok "백업 30h 멈춤 → 빨강" || bad "🔴 멈춘 백업을 놓쳤다(rc=$rc)"
out=$(WN -e MAX_DISK_PCT=0); rc=$?
[ $rc -eq 1 ] && echo "$out" | grep -q '데이터 볼륨' && ok "디스크 기준 초과 → 빨강" || bad "🔴 디스크 판정 공허(rc=$rc)"
out=$(WN -e MAX_BACKUP_AGE_H=9999 -e WATCH_NOW=$((NOW+181*3600))); rc=$?
[ $rc -eq 1 ] && echo "$out" | grep -q '복원 판정이' && ! echo "$out" | grep -q 'backup 이' && ok "복원 판정 181h → 빨강(백업과 독립)" || bad "🔴 복원 판정 나이를 놓쳤다(rc=$rc)"
out=$(WN -e PREFIX=nothing/); rc=$?
[ $rc -eq 1 ] && echo "$out" | grep -q '헬스 키가 없거나' && ok "헬스 키 없음 → 빨강" || bad "🔴 키 없음을 통과시켰다(rc=$rc)"
out=$(WN -e MIN_TABLES=999); rc=$?
[ $rc -eq 1 ] && ok "테이블 기준 → 빨강" || bad "🔴 테이블 판정 공허(rc=$rc)"

echo "── ⑩ install.sh — CUTOVER_DONE 은 내려가지 않는다 (systemctl 목)"
X 'printf "#!/bin/sh\nexit 0\n" > /usr/local/bin/systemctl; chmod +x /usr/local/bin/systemctl'
X 'bash /opt/src/install.sh --region r --bucket b --prefix prod-db/ --cutover-done >/dev/null && bash /opt/src/install.sh --region r --bucket b --prefix prod-db/ >/dev/null'
X 'grep -qx CUTOVER_DONE=1 /etc/devquest-db.env' && ok "재설치 뒤에도 CUTOVER_DONE=1" || bad "🔴 CUTOVER_DONE 이 0 으로 내려갔다"
X 'test -s /var/lib/postgresql/.devquest-cutover-done' && ok "--cutover-done 이 데이터 볼륨 표식을 만든다" || bad "표식 없음"
X 'rm -f /etc/devquest-db.env && bash /opt/src/install.sh --region r --bucket b --prefix prod-db/ >/dev/null && grep -qx CUTOVER_DONE=1 /etc/devquest-db.env' \
  && ok "/etc 가 사라져도(인스턴스 교체) 표식에서 1 복원" || bad "🔴 인스턴스 교체 뒤 가드가 열렸다"
X 'bash /opt/src/install.sh --region r --bucket b --prefix postgres/ >/dev/null 2>&1' && bad "학습 prefix 를 받았다(B6-6)" || ok "학습 prefix postgres/ 거부"

echo
echo "── 결과: 통과 $PASS · 실패 $FAIL"
[ "$FAIL" -eq 0 ]
