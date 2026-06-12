#!/usr/bin/env bash
# gh pr create 전 QA 리뷰 실행 여부 강제 확인
# chore/, docs/ 브랜치는 면제
# 마커 파일에 HEAD SHA가 기록되어 있어야 통과 (단순 존재 확인 X)

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
  echo "   qa-reviewer 에이전트를 실행하면 마커가 자동 생성됩니다." >&2
  echo "   브랜치: $BRANCH" >&2
  exit 2
fi

HEAD_SHA=$(git rev-parse HEAD 2>/dev/null)
MARKER_SHA=$(cat "$QA_MARKER" 2>/dev/null | tr -d '[:space:]')

if [ "$HEAD_SHA" != "$MARKER_SHA" ]; then
  echo "⛔ PR 생성 차단: QA가 현재 커밋 기준으로 실행되지 않았습니다." >&2
  echo "   현재 HEAD : $HEAD_SHA" >&2
  echo "   QA 실행 시점: ${MARKER_SHA:-없음}" >&2
  echo "   커밋이 추가됐거나 QA를 건너뛴 경우 — qa-reviewer를 재실행하세요." >&2
  exit 2
fi

echo "✅ QA 확인됨 (branch=$BRANCH, sha=${HEAD_SHA:0:8}). PR 생성 진행합니다." >&2
exit 0
