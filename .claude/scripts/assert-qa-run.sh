#!/usr/bin/env bash
# gh pr create 전 QA 리뷰 실행 여부 강제 확인
# chore/, docs/ 브랜치는 면제

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# gh pr create 명령인지 확인
if ! echo "$COMMAND" | grep -qE "gh pr create"; then
  exit 0
fi

BRANCH=$(git branch --show-current 2>/dev/null)

# chore/ 또는 docs/ 브랜치는 QA 면제
if echo "$BRANCH" | grep -qE "^(chore|docs)/"; then
  exit 0
fi

PROJECT_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
if [ -z "$PROJECT_ROOT" ]; then
  exit 0
fi

QA_MARKER="$PROJECT_ROOT/.claude/qa-cache/$BRANCH"

if [ ! -f "$QA_MARKER" ]; then
  echo "⛔ PR 생성 차단: QA 리뷰가 실행되지 않았습니다." >&2
  echo "   orchestrator 7단계(qa-reviewer)를 먼저 실행하세요." >&2
  echo "   브랜치: $BRANCH" >&2
  echo "" >&2
  echo "   QA 완료 후 orchestrator가 마커를 자동 생성합니다:" >&2
  echo "   .claude/qa-cache/$BRANCH" >&2
  exit 2
fi

echo "✅ QA 마커 확인됨 ($BRANCH). PR 생성 진행합니다." >&2
exit 0
