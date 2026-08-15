#!/usr/bin/env bash
# assert-qa-run.sh 의 HIGH 항소 경로 테스트.
# 실제 findings/원장을 건드리지 않도록 임시 파일로 바꿔치기하고 끝나면 원복한다.
set -uo pipefail
ROOT=$(git rev-parse --show-toplevel)
BRANCH=$(git branch --show-current)
FIND="$ROOT/.claude/qa-cache/$BRANCH.findings.md"
LEDG="$ROOT/.claude/review-ledger.md"
HOOK="$ROOT/.claude/scripts/assert-qa-run.sh"
MARK="$ROOT/.claude/qa-cache/$BRANCH"

cp "$FIND" /tmp/find.bak 2>/dev/null || true
cp "$LEDG" /tmp/ledg.bak
cp "$MARK" /tmp/mark.bak 2>/dev/null || true
git rev-parse HEAD > "$MARK"

P=0; F=0
run() {  # $1=기대 $2=설명
  local got
  got=$(printf '{"tool_input":{"command":"gh pr create --base main"}}' | bash "$HOOK" >/dev/null 2>&1; echo $?)
  if [ "$got" = "$1" ]; then P=$((P+1)); printf '  ok   exp=%s got=%s  %s\n' "$1" "$got" "$2"
  else F=$((F+1)); printf '  FAIL exp=%s got=%s  %s\n' "$1" "$got" "$2"; fi
}

hdr='| ID | 등급 | 상태 | 위치 | 내용 |
|----|------|------|------|------|'

# ① HIGH + fixed → 통과
printf '%s\n| F-1 | HIGH | fixed | x | y |\n' "$hdr" > "$FIND"
run 0 "HIGH + fixed"

# ② HIGH + deferred → 차단 (항소 마커 없음)
printf '%s\n| F-1 | HIGH | deferred | x | y |\n' "$hdr" > "$FIND"
printf '\n| L-99 | `%s/F-1` | HIGH | 내용 | 근거 |\n' "$BRANCH" >> "$LEDG"
run 2 "HIGH + deferred (원장 있어도 차단)"

# ③ HIGH + wontfix + 원장 있으나 마커 없음 → 차단
printf '%s\n| F-1 | HIGH | wontfix | x | y |\n' "$hdr" > "$FIND"
run 2 "HIGH + wontfix + 마커 없음"

# ④ HIGH + wontfix + 마커 있음 → 통과
cp /tmp/ledg.bak "$LEDG"
printf '\n| L-99 | `%s/F-1` | HIGH | 내용 | 사용자 결정 근거 <!-- USER-DECIDED --> |\n' "$BRANCH" >> "$LEDG"
run 0 "HIGH + wontfix + 사용자 결정 마커"

# ⑤ 마커가 **다른 ID** 행에 있으면 통과되면 안 된다
cp /tmp/ledg.bak "$LEDG"
printf '\n| L-98 | `%s/F-9` | HIGH | 무관 | <!-- USER-DECIDED --> |\n' "$BRANCH" >> "$LEDG"
run 2 "마커가 다른 ID 행에 있음 → 차단"

# ⑥ MEDIUM + deferred + 원장 없음 → 차단 (규칙 3 회귀)
cp /tmp/ledg.bak "$LEDG"
printf '%s\n| F-2 | MEDIUM | deferred | x | y |\n' "$hdr" > "$FIND"
run 2 "MEDIUM deferred + 원장 미등재"

# ⑦ open 잔존 → 차단 (규칙 1 회귀)
printf '%s\n| F-3 | LOW | open | x | y |\n' "$hdr" > "$FIND"
run 2 "LOW open 잔존"

cp /tmp/find.bak "$FIND" 2>/dev/null || rm -f "$FIND"
cp /tmp/ledg.bak "$LEDG"
cp /tmp/mark.bak "$MARK" 2>/dev/null || rm -f "$MARK"
rm -f /tmp/find.bak /tmp/ledg.bak /tmp/mark.bak
echo "─────"; echo "통과 $P / 실패 $F"
