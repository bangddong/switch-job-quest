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
  export MOCK_SENTINEL_BACKUP="${MOCK_SENTINEL_BACKUP-1}"
  export MOCK_SENTINEL_RESTORE="${MOCK_SENTINEL_RESTORE-1}"
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
  *"get deploy"*"jsonpath"*) echo 1; exit 0 ;;
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
  *to_regclass*)                echo "$MOCK_SENTINEL_RESTORE"; exit 0 ;;
  *information_schema.tables*)  echo 14; exit 0 ;;
  *"count(*) FROM backup_sentinel"*) echo "$MOCK_SENTINEL_BACKUP"; exit 0 ;;
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
  unset MOCK_PHASE MOCK_POD_EXISTS MOCK_SENTINEL_BACKUP MOCK_SENTINEL_RESTORE \
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
    [ "$rc" -eq "$want" ] && ok "$name" || bad "$name" "종료코드 $rc (기대 $want)  출력: ${out##*$'\n'}"
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
  || bad "⑤ APPS 선언에 daily-api 포함" "빼면 daily-api 의 repair() 가 core-api 버전을 DELETED 로 마킹 → 영구 부팅 불가"

echo
echo "── db-backup.sh ──"
# ⑥ 무엇의 실패를 잡나: 클러스터가 안 떠 있는데 백업이 '성공'한 것처럼 보이는 것
MOCK_PHASE=Pending run_case "⑥ 파드가 Running 이 아니면 중단" nonzero bash "$BACKUP" --local-only
# ⑦ 무엇의 실패를 잡나: 센티넬을 못 심었는데 덤프를 떠서, 복구 판정 기준이 사라지는 것
MOCK_SENTINEL_BACKUP=0 run_case "⑦ 센티넬 삽입 실패 시 중단" nonzero bash "$BACKUP" --local-only
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
MOCK_SENTINEL_RESTORE=1 run_case "⑫ 센티넬이 남아 있으면 실패" nonzero bash "$RESTORE" --expect-absent tok-1
MOCK_SENTINEL_RESTORE=0 run_case "⑬ 센티넬이 없으면 통과" 0 bash "$RESTORE" --expect-absent tok-1

echo
echo "── db-restore.sh 복구 ──"
setup
DUMPF="$SANDBOX/x.dump"; head -c 2048 /dev/zero > "$DUMPF"
MOCK_SENTINEL_RESTORE=1 bash "$RESTORE" --dump "$DUMPF" --sentinel tok-1 >/dev/null 2>&1
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
MOCK_SENTINEL_RESTORE=0 run_case "⑰ 복구 후 센티넬 없으면 실패" nonzero bash -c \
  'd=$(mktemp); head -c 2048 /dev/zero > "$d"; exec bash "$0" --dump "$d" --sentinel tok-1' "$RESTORE"
# ⑱ 무엇의 실패를 잡나: 판정 기준 없이 복구해 성공 여부를 알 수 없게 되는 것
run_case "⑱ --sentinel 없으면 거부" nonzero bash -c \
  'd=$(mktemp); head -c 2048 /dev/zero > "$d"; exec bash "$0" --dump "$d"' "$RESTORE"

echo
echo "════════ 통과 $PASS · 실패 $FAIL ════════"
[ "$FAIL" -eq 0 ]
