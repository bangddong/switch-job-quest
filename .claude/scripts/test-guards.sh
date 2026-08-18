#!/usr/bin/env bash
# `.claude/scripts/` 가드들의 회귀 테스트. 로컬·CI 양쪽에서 돈다.
#
# ══ 왜 필요한가 (원장 L-11, 2026-08-15에 해소) ══
#
# 이 레포의 가드는 bash·python 뒤섞인 스크립트인데 **자동 테스트가 0건**이었다.
# 그동안 어떤 일이 있었나:
#   08-11  훅 12개가 mode 644 = 한 번도 실행 안 됨 (#377)
#   08-14  되살린 가드가 너무 넓어 **위임 워크플로가 통째로 막힘** (#383)
#   08-14  그 수정이 만든 폴백 파서가 **jq 부재 시 가드를 소멸**시킴 (QA F-5)
#   08-15  따옴표 미인식으로 정상 명령 오탐 → **리뷰어가 실제로 우회** (원장 L-21)
#
# 🔴 **이 스위트가 실제로 회귀를 잡았다.** L-21을 고치며 겹따옴표 안을 통째로 마스킹했는데,
#    셸에서 `$(...)`는 겹따옴표 안에서도 살아있다 → `mkdir -p "$(rm -rf be/core)"`가 뚫렸다.
#    기존 적대적 케이스가 즉시 FAIL을 냈다. 스위트가 없었으면 조용히 나갔을 변경이다.
#
# ── 케이스 파일 형식 ──
#   *.txt  : `기대종료코드 <TAB> 명령 <TAB> 설명`  (명령의 \n 은 printf %b로 전개)
#   *.json : [{"exp":0, "in":{...훅 입력 JSON...}, "note":"..."}]  또는
#            [{"s":"스크립트명", "c":"명령", "note":"..."}]  ← 전부 통과(0) 기대인 오탐 프로브
#
# ⚠️ **오탐 케이스를 지우지 마라.** 차단 케이스만 남기면 "전부 막으면 만점"이 되어
#    가드를 조이는 방향으로만 회귀한다. 오탐이 잦은 가드는 우회당하고, 우회는 가드를 침식한다.

set -uo pipefail
ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "git 레포가 아닙니다"; exit 0; }
cd "$ROOT" || exit 1
T="$ROOT/.claude/scripts/tests"
PASS=0; FAIL=0

echo "── 가드 회귀 테스트 ──"

# $1=스크립트 $2=케이스파일 $3=라벨
run_txt() {
  local script="$ROOT/.claude/scripts/$1" file="$T/$2" label="$3" p=0 f=0
  [ -f "$script" ] && [ -f "$file" ] || { echo "  ⚠️  건너뜀 ($1 또는 $2 없음)"; return; }
  while IFS=$'\t' read -r exp raw note; do
    [ -n "${raw:-}" ] || continue
    local c got
    c=$(printf '%b' "$raw")
    got=$(python3 -c 'import json,sys;print(json.dumps({"tool_input":{"command":sys.argv[1]}}))' "$c" \
          | bash "$script" >/dev/null 2>&1; echo $?)
    if [ "$got" = "$exp" ]; then p=$((p+1)); else
      f=$((f+1)); printf '  ❌ %-28s exp=%s got=%s  %s\n' "$1" "$exp" "$got" "${note:-$c}"
    fi
  done < "$file"
  printf '  %-28s %2d/%d  %s\n' "$1" "$p" "$((p+f))" "$label"
  PASS=$((PASS+p)); FAIL=$((FAIL+f))
}

# 훅 입력 JSON을 그대로 주는 케이스 (경로 가드용)
run_json() {
  local script="$ROOT/.claude/scripts/$1" file="$T/$2" label="$3"
  [ -f "$script" ] && [ -f "$file" ] || { echo "  ⚠️  건너뜀 ($1 또는 $2 없음)"; return; }
  local out
  out=$(python3 - "$script" "$file" <<'PY'
import json,subprocess,sys
script,file=sys.argv[1],sys.argv[2]
p=f=0
for c in json.load(open(file,encoding='utf-8')):
    r=subprocess.run(['bash',script],input=json.dumps(c['in']),capture_output=True,text=True)
    if r.returncode==c['exp']: p+=1
    else:
        f+=1; print(f"  ❌ exp={c['exp']} got={r.returncode}  {c.get('note','')}")
print(f"__RESULT__ {p} {f}")
PY
)
  echo "$out" | grep -v '^__RESULT__' || true
  local p f2
  p=$(echo "$out" | awk '/^__RESULT__/{print $2}'); f2=$(echo "$out" | awk '/^__RESULT__/{print $3}')
  printf '  %-28s %2d/%d  %s\n' "$1" "$p" "$((p+f2))" "$label"
  PASS=$((PASS+p)); FAIL=$((FAIL+f2))
}

# 오탐 프로브: 전부 통과(exit 0)해야 한다
run_probes() {
  local file="$T/probes.json"
  [ -f "$file" ] || return
  local out
  out=$(python3 - "$ROOT" "$file" <<'PY'
import json,subprocess,sys,os
root,file=sys.argv[1],sys.argv[2]
p=f=0
for c in json.load(open(file,encoding='utf-8')):
    sp=os.path.join(root,'.claude','scripts',c['s'])
    if not os.path.exists(sp): continue
    r=subprocess.run(['bash',sp],input=json.dumps({"tool_input":{"command":c['c']}}),
                     capture_output=True,text=True)
    if r.returncode==2:
        f+=1; print(f"  ❌ 오탐 {c['s']}: {c['note']}")
    else: p+=1
print(f"__RESULT__ {p} {f}")
PY
)
  echo "$out" | grep -v '^__RESULT__' || true
  local p f2
  p=$(echo "$out" | awk '/^__RESULT__/{print $2}'); f2=$(echo "$out" | awk '/^__RESULT__/{print $3}')
  printf '  %-28s %2d/%d  %s\n' "(오탐 프로브)" "$p" "$((p+f2))" "가드를 설명하는 문서·명령이 막히지 않는가"
  PASS=$((PASS+p)); FAIL=$((FAIL+f2))
}

# 스크립트명이 케이스 안에 있는 형식 (여러 가드를 한 파일에서 검사)
run_multi() {
  local file="$T/$1" label="$2"
  [ -f "$file" ] || { echo "  ⚠️  건너뜀 ($1 없음)"; return; }
  local out
  out=$(python3 - "$ROOT" "$file" <<'PY'
import json,subprocess,sys,os
root,file=sys.argv[1],sys.argv[2]
p=f=0
for c in json.load(open(file,encoding='utf-8')):
    sp=os.path.join(root,'.claude','scripts',c['s'])
    if not os.path.exists(sp): continue
    r=subprocess.run(['bash',sp],input=json.dumps(c['in']),capture_output=True,text=True)
    if r.returncode==c['exp']: p+=1
    else:
        f+=1; print(f"  ❌ {c['s']} exp={c['exp']} got={r.returncode}  {c.get('note','')}")
print(f"__RESULT__ {p} {f}")
PY
)
  echo "$out" | grep -v '^__RESULT__' || true
  local p f2
  p=$(echo "$out" | awk '/^__RESULT__/{print $2}'); f2=$(echo "$out" | awk '/^__RESULT__/{print $3}')
  printf '  %-28s %2d/%d  %s\n' "(경로 가드)" "$p" "$((p+f2))" "$label"
  PASS=$((PASS+p)); FAIL=$((FAIL+f2))
}

run_json  assert-orchestrator-path.sh aop-cases.json      "에이전트 판별 · fail-closed"
run_multi path-guard-cases.json                           "be/fe 경로 가드 · 상속 회귀 방지"
run_txt  assert-qa-readonly.sh       qa-guard-cases.txt   "정상 리뷰 명령 + 우회 시도"
run_txt  assert-qa-readonly.sh       qa-guard-cases2.txt  "적대적"
run_txt  assert-qa-readonly.sh       qa-cases3.txt        "따옴표 오탐(L-21) + 적대적"
run_txt  assert-qa-readonly.sh       qa-cases4.txt        "셸 키워드·ANSI-C 우회(F-1·F-9)"
run_txt  assert-no-admin.sh          hook-cases.txt       "브랜치 보호 우회 경로"
run_txt  assert-no-main-push.sh      push-guard-cases.txt "main push 차단 + heredoc 오탐"
run_probes

# 게이트 자체 테스트 — findings/원장을 임시로 바꿔치기하므로 별도 스크립트다.
# ⚠️ 실제 파일을 건드렸다가 원복하므로, 실패해도 원복이 돌도록 스크립트 안에서 관리한다.
if [ -x "$T/qa-gate-test.sh" ]; then
  out=$(bash "$T/qa-gate-test.sh" 2>&1)
  echo "$out" | grep -E '^  FAIL' || true
  p=$(echo "$out" | awk '/^통과/{print $2}'); f=$(echo "$out" | awk '/^통과/{print $5}')
  printf '  %-28s %2s/%s  %s\n' "assert-qa-run.sh" "$p" "$((p+f))" "HIGH 항소 경로 · 규칙 1·3 회귀"
  PASS=$((PASS+p)); FAIL=$((FAIL+f))
fi

echo
# 🔴 **거짓 통과 방어.** 케이스 파일이 비거나 러너가 조용히 건너뛰면(파일명 오타·경로 변경)
#    실패 0건으로 "통과"가 된다 — 커버리지가 사라졌는데 초록이 뜬다.
#    실제로 이번 PR 초판에 배선 안 된 케이스 파일이 하나 있었다(08-15 QA F-3).
#    → 총 건수 하한을 박는다. 케이스를 **추가**하면 이 숫자도 함께 올린다.
#    ⚠️ 낮출 때는 반드시 **무엇을 지웠는지** 근거를 남긴다.
#       08-17: qa-effect-guard.sh 삭제(배선된 적 없는 죽은 코드) → 9건 제거, 152 → 143.
#       08-18: assert-no-main-push 오탐 회귀 케이스 6건 추가(L-29), 143 → 149.
MIN_CASES=149
if [ "$((PASS+FAIL))" -lt "$MIN_CASES" ]; then
  echo "❌ 케이스 수가 줄었다: $((PASS+FAIL)) < ${MIN_CASES}"
  echo "   케이스 파일이 비었거나 러너 배선이 끊겼을 가능성이 높다."
  echo "   의도적으로 줄였다면 이 스크립트의 MIN_CASES를 함께 낮춰라(근거를 커밋 메시지에)."
  exit 1
fi
if [ "$FAIL" -gt 0 ]; then
  echo "❌ 가드 테스트 실패 — 통과 ${PASS} / 실패 ${FAIL}"
  echo "   가드를 고칠 때는 **오탐과 차단을 함께** 봐야 한다. 한쪽만 보면 반드시 반대쪽이 깨진다."
  exit 1
fi
echo "✅ 가드 테스트 통과 — ${PASS}건"
