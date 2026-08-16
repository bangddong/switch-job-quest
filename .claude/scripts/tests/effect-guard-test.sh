#!/usr/bin/env bash
# qa-effect-guard.sh 회귀 테스트 — F-11·F-15·F-16을 함께 고정한다.
set -uo pipefail
ROOT=$(git rev-parse --show-toplevel); cd "$ROOT"
G="$ROOT/.claude/scripts/qa-effect-guard.sh"
S="$ROOT/.claude/.effect-state"
A1='{"agent_type":"qa-reviewer","agent_id":"agent-AAA"}'
A2='{"agent_type":"qa-reviewer","agent_id":"agent-BBB"}'
OTHER='{"agent_type":"be-feature-builder","agent_id":"x"}'
P=0; F=0
chk() { if [ "$2" = "$3" ]; then P=$((P+1)); printf '  ok   exp=%s got=%s  %s\n' "$2" "$3" "$1"
        else F=$((F+1)); printf '  FAIL exp=%s got=%s  %s\n' "$2" "$3" "$1"; fi; }
run() { echo "$1" | bash "$G" "$2" >/dev/null 2>&1; echo $?; }

rm -rf "$S"
chk "baseline 전무 → fail-closed (F-11)" 2 "$(run "$A1" verify)"

rm -rf "$S"; run "$A1" snapshot >/dev/null
chk "정상: snapshot→verify" 0 "$(run "$A1" verify)"
chk "같은 에이전트 2회차 Stop (F-15)" 0 "$(run "$A1" verify)"
chk "같은 에이전트 3회차 Stop (F-15)" 0 "$(run "$A1" verify)"

# F-16: Start 없이 다른 에이전트가 verify → 이전 .done 편승 시도
chk "다른 에이전트가 Start 없이 verify (F-16)" 2 "$(run "$A2" verify)"

# 새 에이전트가 정상적으로 Start 하면 통과
run "$A2" snapshot >/dev/null
chk "새 에이전트가 Start 후 verify" 0 "$(run "$A2" verify)"

# 실제 변경 탐지
run "$A1" snapshot >/dev/null
printf '\n<!-- t -->\n' >> README.md
chk "추적 파일 변경 → 검출" 2 "$(run "$A1" verify)"
git checkout -- README.md

# qa-reviewer 아닌 에이전트는 관여 안 함
chk "다른 에이전트 타입 → 무관" 0 "$(run "$OTHER" verify)"

# F-17: agent_id 부재 → fail-closed (자매 가드와 일관)
rm -rf "$S"; run "$A1" snapshot >/dev/null
chk "agent_id 없음 → fail-closed (F-17)" 2 "$(run '{"agent_type":"qa-reviewer"}' verify)"

rm -rf "$S"
echo "─────"; echo "통과 $P / 실패 $F"
