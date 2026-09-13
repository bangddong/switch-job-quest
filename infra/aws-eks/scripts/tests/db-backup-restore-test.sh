#!/usr/bin/env bash
#
# db-backup.sh / db-restore.sh 회귀 테스트 — **AWS·클러스터 접촉 0**
#
# kubectl·aws·tofu·pg_restore 를 목으로 갈아끼우고 호출 흔적을 파일에 남겨 검사한다.
# 유료 세션에서 발견하면 분 단위로 돈이 나가는 실수를 $0 에 잡는 것이 목적이다.
#
# ⚠️ 각 케이스에 **"무엇의 실패를 잡나"** 를 적는다(#414 QA F-1 조치). 적을 수 없는 케이스는
#    테스트가 아니라 장식이다.
#
#   bash infra/aws-eks/scripts/tests/db-backup-restore-test.sh
#
# ── 반증 기록 (2026-09-12) — 통과 18/18 은 그 자체로 판정력의 증거가 아니다 ──────
#
#   반증①  exec 에 -it 주입                  → ① 깨짐 ✅
#   반증②  pg_dumpall 로 교체                → ②⑪ 깨짐 ✅
#   반증③  --env 재주입                      → ③ 깨짐 ✅
#   반증④  --exit-on-error 제거              → ④ 깨짐 ✅
#   반증⑤  APPS 에서 daily-api 제거          → ⑤⑭ 깨짐 ✅
#   반증C   --expect-absent 판정 무력화       → ⑫ 깨짐 ✅
#   반증D   S3 크기 대조 제거                 → ⑩ 깨짐 ✅
#
# ── 유료 세션(2026-09-13) 실측 후 추가된 반증 ────────────────────────────
#   반증⑥  pg_restore -l 에 /dev/stdin 재주입   → ⑥ 깨짐 ✅
#   반증⑦  CASE WHEN to_regclass 재주입         → ⑦⑬⑮ 깨짐 ✅
#   반증⑧  판정 쿼리 ON_ERROR_STOP 제거          → ⑧ 깨짐 ✅
#   반증⑨  판정불가 분기 제거                    → ⑨ 깨짐 ✅
#
# ⚠️ **반증⑦은 첫 시도가 반증하지 못했다** — 정규식이 4칸 들여쓰기를 기대했는데 실제는 2칸이라
#    주입 자체가 no-op 이었고, "안 깨짐 = 검사가 무력함"으로 읽힐 뻔했다.
#    ***반증이 실패했을 때 의심할 곳은 검사만이 아니라 반증 자체다.*** (#416 에서 같은 형태)
#
# 🔴 **초판은 ④⑤가 무력했다.** 주석("--exit-on-error : psql 의 ON_ERROR_STOP 에 해당")에
#    매칭돼서 실제 플래그를 지워도 통과했다. 반증을 돌리기 전까지 몰랐다.
#    08-12 F-5 → #416 verify 마커 → 여기. **같은 병의 세 번째 재발이다.**
#
# 🔴 **⑨는 한동안 "거짓 통과"였다.** `VAR=x func` 할당이 함수 반환 뒤에도 남아 앞 케이스의
#    `MOCK_PHASE=Pending` 이 흘러들었고, ⑨는 버킷과 무관한 이유로 죽으면서 통과했다.
#    누수를 막자 진짜 원인이 드러났다 — `${VAR:-기본값}` 의 `:-` 가 빈 문자열도 미설정으로
#    취급해 "빈 버킷" 시나리오 자체를 표현할 수 없었다.
#    ***통과한 검사가 무엇 때문에 통과했는지는 반증해봐야 안다.***
#
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
BACKUP="$ROOT/infra/aws-eks/scripts/db-backup.sh"
RESTORE="$ROOT/infra/aws-eks/scripts/db-restore.sh"

PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ $1"; [ -n "${2:-}" ] && echo "       $2"; }

# ── 목 환경 ──────────────────────────────────────────────────────────────
setup() {
  SANDBOX="$(mktemp -d)"
  BIN="$SANDBOX/bin"; mkdir -p "$BIN"
  TRACE="$SANDBOX/trace"; : > "$TRACE"

  # 시나리오 기본값.
  # 🔴 `:-` 가 아니라 `-` 다. `:-` 는 **빈 문자열도 미설정으로 취급**해서
  #    `MOCK_BUCKET=""` 시나리오(= tofu output 이 null 인 경우)를 표현할 수 없었다.
  #    그 케이스는 한동안 **다른 이유로 죽어서 통과하고 있었다**(누수된 MOCK_PHASE).
  export MOCK_PHASE="${MOCK_PHASE-Running}"
  export MOCK_POD_EXISTS="${MOCK_POD_EXISTS-1}"
  # 센티넬 상태는 **두 축**이다 — 테이블이 있는가(t/f)와 해당 토큰이 몇 행인가.
  # 판정 쿼리를 두 문장으로 쪼갠 뒤(파스타임 해석 문제) 목도 같이 쪼개야 했다.
  export MOCK_SENTINEL_TABLE="${MOCK_SENTINEL_TABLE-t}"
  export MOCK_SENTINEL_COUNT="${MOCK_SENTINEL_COUNT-1}"
  export MOCK_DUMP_BYTES="${MOCK_DUMP_BYTES-2048}"
  export MOCK_RESTORE_RC="${MOCK_RESTORE_RC-0}"
  export MOCK_BUCKET="${MOCK_BUCKET-devquest-eks-backups-seoul}"
  export MOCK_S3_SIZE_DELTA="${MOCK_S3_SIZE_DELTA:-0}"
  export TRACE

  cat > "$BIN/kubectl" <<'MOCK'
#!/usr/bin/env bash
echo "kubectl $*" >> "$TRACE"
args="$*"
case "$args" in
  *"get pod"*"jsonpath"*)  echo "$MOCK_PHASE"; exit 0 ;;
  *"get pod"*)             [ "$MOCK_POD_EXISTS" = "1" ] && exit 0 || exit 1 ;;
  *"get deploy"*"jsonpath"*)
    # 특정 앱의 replicas 조회만 실패시킨다 (QA F-1 재현용)
    case "$args" in *"$MOCK_FAIL_REPLICAS_OF"*) [ -n "$MOCK_FAIL_REPLICAS_OF" ] && exit 1 ;; esac
    echo 1; exit 0 ;;
  *"get deploy"*)          exit 0 ;;
  *"scale deploy"*)        exit 0 ;;
  *wait*)                  exit 0 ;;
esac
# exec 계열 — 원격 명령은 마지막 인자.
# 🔴 stdin 을 **무조건 cat 하면 안 된다.** stdin 이 파이프가 아닌 호출(pg_isready 등)에서
#    영구 블록한다. 실제로 첫 실행이 이것 때문에 2분 타임아웃으로 죽었다.
#    → 원격 명령이 stdin 을 쓴다고 선언한 경우(`-f -`)에만 읽는다.
remote="${!#}"
sql=""
case "$remote" in
  *"-f -"*) sql="$(cat)"; echo "SQL $sql" >> "$TRACE" ;;
esac
case "$args $sql" in
  *pg_isready*)                 exit 0 ;;
  *pg_dumpall*)                 echo "pg_dumpall 은 절대 쓰면 안 된다" >&2; exit 99 ;;
  *pg_dump*)                    head -c "$MOCK_DUMP_BYTES" /dev/zero; exit 0 ;;
  *"pg_restore -l"*)            echo "; Archive"; echo "1; 2200 TABLE x"; exit 0 ;;
  *pg_restore*)                 cat >/dev/null 2>&1; exit "$MOCK_RESTORE_RC" ;;
  *"CREATE TABLE IF NOT EXISTS backup_sentinel"*) exit 0 ;;
  *"to_regclass"*"IS NOT NULL"*) echo "$MOCK_SENTINEL_TABLE"; exit 0 ;;
  *information_schema.tables*)  echo 14; exit 0 ;;
  *"count(*) FROM backup_sentinel"*) echo "$MOCK_SENTINEL_COUNT"; exit 0 ;;
esac
exit 0
MOCK

  cat > "$BIN/aws" <<'MOCK'
#!/usr/bin/env bash
echo "aws $*" >> "$TRACE"
case "$*" in
  *head-object*)
    # 로컬 크기 + 델타 → 불일치 시나리오를 만든다
    echo $(( MOCK_DUMP_BYTES + MOCK_S3_SIZE_DELTA )); exit 0 ;;
  *"s3 cp"*) exit 0 ;;
esac
exit 0
MOCK

  cat > "$BIN/tofu" <<'MOCK'
#!/usr/bin/env bash
echo "tofu $*" >> "$TRACE"
case "$*" in
  *"output -raw backup_bucket"*) printf '%s' "$MOCK_BUCKET"; [ -n "$MOCK_BUCKET" ] && exit 0 || exit 1 ;;
esac
exit 0
MOCK

  # 노트북 pg_restore 는 일부러 두지 않는다 — 스크립트가 파드로 폴백하는 경로를 타게 한다.
  chmod +x "$BIN"/*
  export PATH="$BIN:$PATH"
  export OUT_DIR="$SANDBOX/out"
  export BOOTSTRAP_DIR="$SANDBOX"   # tofu 목이 어디서든 답한다
}
# 🔴 `VAR=x func` 의 할당은 **함수 반환 후에도 셸에 남는다**(POSIX 함수의 알려진 성질).
#    setup 의 `export MOCK_...="${MOCK_...:-기본값}"` 과 겹쳐 시나리오가 다음 케이스로 샌다.
#    실제로 ⑥(Pending)이 ⑪(정상)까지 흘러 정상 경로가 거짓 실패했다.
teardown() {
  rm -rf "$SANDBOX"
  unset MOCK_PHASE MOCK_POD_EXISTS MOCK_SENTINEL_TABLE MOCK_SENTINEL_COUNT \
        MOCK_DUMP_BYTES MOCK_RESTORE_RC MOCK_BUCKET MOCK_S3_SIZE_DELTA
}


run_case() { # name expected_rc script args...
  local name="$1" want="$2"; shift 2
  setup
  local out rc
  out="$( "$@" 2>&1 </dev/null )"; rc=$?
  LAST_OUT="$out"; LAST_TRACE="$(cat "$TRACE")"
  if [ "$want" = "nonzero" ]; then
    [ "$rc" -ne 0 ] && ok "$name" || bad "$name" "종료코드 0 (실패해야 했다)"
  else
    [ "$rc" -eq "$want" ] && ok "$name" || bad "$name" "종료코드 $rc (기대 $want)  출력: ${out##*$'
'}"
  fi
  teardown
}

echo "── 정적 검사 (스크립트 본문) ──"
#
# 🔴 **주석이 아니라 선언문에만 매칭한다.** 이 규칙이 없으면 검사가 "그 코드를 설명하는
#    주석"에 걸려서, 코드를 지워도 통과한다. 이 레포에서 세 번 반복된 실패다:
#      2026-08-12 F-5 → #416 verify 마커 → 이 스위트의 초판(④⑤가 실제로 무력했다,
#      `--exit-on-error` 를 지워도 아무 테스트도 깨지지 않았다).
#    그래서 모든 정적 검사는 `code` 를 통과한 텍스트만 본다.
code() { grep -vE '^[[:space:]]*#' "$@"; }

# ① 무엇의 실패를 잡나: TTY 가 붙어 -Fc 아카이브가 조용히 깨지는 것
code "$BACKUP" "$RESTORE" | grep -qE 'exec[^|]*(-it|-ti)\b|exec[^|]*[[:space:]]-t[[:space:]]' \
  && bad "① exec 에 TTY 없음" "TTY 가 붙으면 -Fc 아카이브가 조용히 깨진다" \
  || ok "① exec 에 TTY 없음"

# ② 무엇의 실패를 잡나: 롤 SCRAM 해시가 덤프에 실려 S3 로 나가는 것
code "$BACKUP" | grep -q "pg_dumpall" \
  && bad "② pg_dumpall 미사용" "롤 SCRAM 해시가 덤프에 실려 S3 로 나간다" \
  || ok "② pg_dumpall 미사용"

# ③ 무엇의 실패를 잡나: 존재하지 않는 플래그로 런타임에 죽는 것
code "$BACKUP" "$RESTORE" | grep -q -- "--env" \
  && bad "③ kubectl exec --env 미사용" "그런 플래그가 없다 → unknown flag 로 죽는다" \
  || ok "③ kubectl exec --env 미사용"

# ④ 무엇의 실패를 잡나: 조용한 부분 복구(에러를 뱉으며 exit 0) + 반쯤 복구된 DB 가 남는 것
#    🔑 **같은 한 줄에 두 플래그가 다 있어야** 한다 — 주석에 따로 언급된 것으로는 통과 못 한다.
code "$RESTORE" | grep -q "pg_restore .*--single-transaction.*--exit-on-error" \
  && ok "④ pg_restore 호출에 --single-transaction + --exit-on-error" \
  || bad "④ pg_restore 호출에 --single-transaction + --exit-on-error" "없으면 에러를 뱉으며 exit 0 = 조용한 부분 복구"

# ⑤ 무엇의 실패를 잡나: daily-api 의 repair() 가 core-api 버전을 DELETED 로 마킹 → 영구 부팅 불가
#    🔑 **APPS 선언문**에 있어야 한다. 주석에 daily-api 를 언급하는 것으로는 통과 못 한다.
code "$RESTORE" | grep -qE '^APPS=.*daily-api' \
  && ok "⑤ APPS 선언에 daily-api 포함" \
  || bad "⑤ APPS 선언에 daily-api 포함" "빼면 daily-api 커넥션이 남아 pg_restore --clean 과 충돌한다"

# ── 아래 둘은 **목이 못 잡아서 유료 세션에서 터진 것**을 고정한 것이다 ──────────
#    목은 `pg_restore -l` 호출의 **모양**을 검증했지 그 호출이 동작하는지는 검증하지 않았다.
#    ***목이 검증하는 것은 형태이지 의미가 아니다.*** 정적 규칙으로 내려서 재발을 막는다.

# ⑥ 무엇의 실패를 잡나: 덤프가 멀쩡한데 "아카이브가 깨졌다"고 보고하는 것 (2026-09-13 실측)
#    `/dev/stdin` 을 **파일 이름으로** 주면 pg_restore 가 seek 하려 들고 파이프는 seek 이 안 된다
#    → `did not find magic string in file header`. 파일명을 생략해야 스트리밍 모드로 읽는다.
code "$BACKUP" | grep -q "pg_restore -l /dev/stdin" \
  && bad "⑥ pg_restore -l 에 /dev/stdin 미전달" "파이프는 seek 이 안 된다 → 멀쩡한 덤프를 깨졌다고 보고" \
  || ok "⑥ pg_restore -l 에 /dev/stdin 미전달"

# ⑦ 무엇의 실패를 잡나: 한 문장 CASE 로 테이블 존재를 분기하려는 것 (2026-09-13 실측)
#    PostgreSQL 은 실행 전에 문장 전체를 파싱·플랜하므로 **타지 않는 분기의 테이블 이름도**
#    그 시점에 해석된다 → `relation "backup_sentinel" does not exist`.
#    CASE 는 런타임 분기이지 파스타임 보호가 아니다. 존재 확인과 카운트는 **두 문장**이어야 한다.
code "$RESTORE" | grep -q "CASE WHEN to_regclass" \
  && bad "⑦ to_regclass 를 CASE 한 문장으로 쓰지 않음" "파스타임에 해석돼 ERROR — CASE 는 파스타임 보호가 아니다" \
  || ok "⑦ to_regclass 를 CASE 한 문장으로 쓰지 않음"

# ⑧ 무엇의 실패를 잡나: 판정 쿼리가 ERROR 를 뱉고도 exit 0 을 내는 것
#    이 스크립트가 **복구 경로에 대해 경고하는 바로 그 함정**에 판정 경로에서 물렸다.
code "$RESTORE" | grep -q "psql -v ON_ERROR_STOP=1 -tAq" \
  && ok "⑧ 판정 쿼리에도 ON_ERROR_STOP" \
  || bad "⑧ 판정 쿼리에도 ON_ERROR_STOP" "ERROR 를 뱉고도 exit 0 → 빈 값이 판정에 흘러든다"

# ⑨ 무엇의 실패를 잡나: "센티넬이 없다"와 "확인할 수 없다"를 같은 분기로 다루는 것
#    1차 실행이 `count=`(빈 문자열)를 받고 **"센티넬이 아직 있다"** 고 보고했다.
#    멈춘 방향은 안전했지만 원인 표시가 틀렸다 — SOP §2b 의 "판정 불가 vs 재빌드 필요"와 같은 형태.
code "$RESTORE" | grep -q '\[ -z "\$n" \]' \
  && ok "⑨ 판정 불가를 별도 분기로 구분" \
  || bad "⑨ 판정 불가를 별도 분기로 구분" "빈 값을 '센티넬 존재'로 보고하면 원인을 못 찾는다"

echo
echo "── db-backup.sh ──"
# ⑥ 무엇의 실패를 잡나: 클러스터가 안 떠 있는데 백업이 '성공'한 것처럼 보이는 것
MOCK_PHASE=Pending run_case "⑥ 파드가 Running 이 아니면 중단" nonzero bash "$BACKUP" --local-only
# ⑦ 무엇의 실패를 잡나: 센티넬을 못 심었는데 덤프를 떠서, 복구 판정 기준이 사라지는 것
MOCK_SENTINEL_COUNT=0 run_case "⑦ 센티넬 삽입 실패 시 중단" nonzero bash "$BACKUP" --local-only
# ⑧ 무엇의 실패를 잡나: 빈/깨진 덤프를 백업으로 인정하는 것
MOCK_DUMP_BYTES=0 run_case "⑧ 빈 덤프면 중단" nonzero bash "$BACKUP" --local-only
# ⑨ 무엇의 실패를 잡나: tofu output 이 null 일 때 s3:// 로 시작하는 쓰레기 경로에 업로드
MOCK_BUCKET="" run_case "⑨ 버킷 이름이 비면 중단" nonzero bash "$BACKUP"
# ⑩ 무엇의 실패를 잡나: 업로드가 잘렸는데 성공으로 보고하는 것
MOCK_S3_SIZE_DELTA=-10 run_case "⑩ S3 크기 불일치면 중단" nonzero bash "$BACKUP"
# ⑪ 정상 경로
run_case "⑪ 정상 백업은 성공" 0 bash "$BACKUP"

echo
echo "── db-restore.sh --expect-absent (판정력의 원천) ──"
# ⑫ 🔴 가장 중요한 케이스. 볼륨이 안 지워졌는데 통과시키면 리허설 전체가 무의미해진다.
MOCK_SENTINEL_TABLE=t MOCK_SENTINEL_COUNT=1 run_case "⑫ 센티넬이 남아 있으면 실패" nonzero bash "$RESTORE" --expect-absent tok-1
MOCK_SENTINEL_TABLE=f run_case "⑬ 센티넬이 없으면 통과 (테이블 자체가 없다)" 0 bash "$RESTORE" --expect-absent tok-1

echo
echo "── db-restore.sh 복구 ──"
setup
DUMPF="$SANDBOX/x.dump"; head -c 2048 /dev/zero > "$DUMPF"
MOCK_SENTINEL_TABLE=t MOCK_SENTINEL_COUNT=1 bash "$RESTORE" --dump "$DUMPF" --sentinel tok-1 >/dev/null 2>&1
tr_all="$(cat "$TRACE")"
# ⑭ 무엇의 실패를 잡나: 앱이 뜬 채로 복구해 Flyway 와 경쟁하는 것 (순서를 줄번호로 본다)
sc="$(grep -n "scale deploy core-api --replicas=0" <<< "$tr_all" | head -1 | cut -d: -f1)"
sd="$(grep -n "scale deploy daily-api --replicas=0" <<< "$tr_all" | head -1 | cut -d: -f1)"
pr="$(grep -n "pg_restore" <<< "$tr_all" | head -1 | cut -d: -f1)"
if [ -n "$sc" ] && [ -n "$sd" ] && [ -n "$pr" ] && [ "$sc" -lt "$pr" ] && [ "$sd" -lt "$pr" ]; then
  ok "⑭ 두 앱 모두 pg_restore **전에** replicas=0"
else
  bad "⑭ 두 앱 모두 pg_restore 전에 replicas=0" "core=$sc daily=$sd restore=$pr"
fi
# ⑮ 무엇의 실패를 잡나: 복구 후 앱을 안 되돌려 서비스가 죽은 채 남는 것
grep -q "scale deploy core-api --replicas=1" <<< "$tr_all" \
  && ok "⑮ 복구 후 앱 복원" || bad "⑮ 복구 후 앱 복원" "$tr_all"
teardown

# ⑯ 무엇의 실패를 잡나: pg_restore 가 실패했는데 성공으로 보고하는 것
MOCK_RESTORE_RC=1 run_case "⑯ pg_restore 실패 시 중단" nonzero bash -c \
  'd=$(mktemp); head -c 2048 /dev/zero > "$d"; exec bash "$0" --dump "$d" --sentinel tok-1' "$RESTORE"
# ⑰ 무엇의 실패를 잡나: 복구는 됐는데 **다른 덤프**를 복구한 것 (센티넬 불일치)
MOCK_SENTINEL_TABLE=t MOCK_SENTINEL_COUNT=0 run_case "⑰ 복구 후 센티넬 없으면 실패" nonzero bash -c \
  'd=$(mktemp); head -c 2048 /dev/zero > "$d"; exec bash "$0" --dump "$d" --sentinel tok-1' "$RESTORE"
echo
echo "── 실패 경로 (QA F-1·F-2 재현 — 22건이 전부 '순조로운 흐름'만 봤다) ──"

# ⑲ 무엇의 실패를 잡나: 🔴 **복구·판정이 성공한 뒤 앱이 내려간 채 남는 것.**
#    초판은 성공 경로 끝에서만 복원해서, die() 로 끝나면 서비스가 죽은 채였다.
setup
DUMPF="$SANDBOX/x.dump"; head -c 2048 /dev/zero > "$DUMPF"
MOCK_RESTORE_RC=1 bash "$RESTORE" --dump "$DUMPF" --sentinel tok-1 >/dev/null 2>&1
tr_all="$(cat "$TRACE")"
if grep -q "scale deploy core-api --replicas=1" <<< "$tr_all"; then
  ok "⑲ pg_restore 실패해도 앱을 되돌린다"
else
  bad "⑲ pg_restore 실패해도 앱을 되돌린다" "die 경로에서 서비스가 죽은 채 남는다"
fi
teardown

# ⑳ 무엇의 실패를 잡나: 🔴 **조용한 사망.** `set -e` 하에서 bare 커맨드치환 대입이 실패하면
#    스크립트가 **아무 말 없이** 죽는다. trap 이 상태는 되돌리므로 비대칭은 안 생기지만,
#    사람은 **왜 멈췄는지 알 수 없다.**
#    🔑 그래서 이 케이스는 *상태*가 아니라 **진단**을 검사한다 — 상태를 검사하면 trap 만으로
#       충족돼 ⑲의 중복이 된다(첫 반증에서 실제로 그랬다: 반증했는데 안 깨졌다).
setup
DUMPF="$SANDBOX/x.dump"; head -c 2048 /dev/zero > "$DUMPF"
out20="$(MOCK_FAIL_REPLICAS_OF=daily-api bash "$RESTORE" --dump "$DUMPF" --sentinel tok-1 2>&1 </dev/null)"
if grep -q "replicas 를 읽지 못했다" <<< "$out20"; then
  ok "⑳ replicas 조회 실패를 진단과 함께 멈춘다"
else
  bad "⑳ replicas 조회 실패를 진단과 함께 멈춘다" "무출력 종료 = 왜 멈췄는지 알 수 없다: ${out20:-（무출력）}"
fi
teardown

# ⑱ 무엇의 실패를 잡나: 판정 기준 없이 복구해 성공 여부를 알 수 없게 되는 것
run_case "⑱ --sentinel 없으면 거부" nonzero bash -c \
  'd=$(mktemp); head -c 2048 /dev/zero > "$d"; exec bash "$0" --dump "$d"' "$RESTORE"

echo
echo "════════ 통과 $PASS · 실패 $FAIL ════════"
[ "$FAIL" -eq 0 ]
