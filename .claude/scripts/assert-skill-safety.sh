#!/usr/bin/env bash
# gh pr create 전 .claude/skills/ 안전성 규칙 검사 (토스 루브릭 6-1, 6-2 채택)
# BLOCKER: 평문 secret / destructive 명령 패턴 → PR 생성 차단 (exit 2)
# 규칙 기반 — False Positive 감수, False Negative 0 지향

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

if ! echo "$COMMAND" | grep -qE "gh pr create"; then
  exit 0
fi

PROJECT_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
[ -z "$PROJECT_ROOT" ] && exit 0
SKILLS_DIR="$PROJECT_ROOT/.claude/skills"
[ -d "$SKILLS_DIR" ] || exit 0

FAIL=0

# 6-2: destructive 명령 패턴
DESTRUCTIVE='(\brm[[:space:]]+-[a-z]*rf?\b|\bdd[[:space:]]+if=|\bmkfs\b|chmod[[:space:]]+(-R[[:space:]]+)?777|--no-verify\b|--force\b|push[[:space:]]+-f\b|:\(\)[[:space:]]*\{)'
HITS=$(grep -rnE "$DESTRUCTIVE" "$SKILLS_DIR" --include='*.md' 2>/dev/null)
if [ -n "$HITS" ]; then
  echo "⛔ 스킬 안전성 위반 — destructive 명령 패턴:" >&2
  echo "$HITS" | head -10 >&2
  FAIL=1
fi

# 6-1: 평문 secret (값 할당 형태만 — 단어 언급은 허용)
SECRET='(api[_-]?key|secret|token|password|credential)[[:space:]]*[:=][[:space:]]*["'"'"']?[A-Za-z0-9_\-]{12,}'
HITS=$(grep -rniE "$SECRET" "$SKILLS_DIR" --include='*.md' 2>/dev/null | grep -viE '\$\{|<[^>]+>|예:|placeholder|your[_-]')
if [ -n "$HITS" ]; then
  echo "⛔ 스킬 안전성 위반 — 평문 secret 의심:" >&2
  echo "$HITS" | head -10 >&2
  FAIL=1
fi

if [ "$FAIL" = "1" ]; then
  echo "   .claude/skills/ 는 sub-agent 프롬프트에 전문 주입됨 — 위 패턴 제거 후 재시도." >&2
  echo "   (금지 예시로 언급이 필요하면 코드 블록 대신 서술로: '강제 푸시 금지' 등)" >&2
  exit 2
fi
exit 0
