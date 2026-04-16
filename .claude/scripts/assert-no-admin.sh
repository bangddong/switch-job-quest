#!/usr/bin/env bash
# gh pr merge --admin 차단
# 브랜치 보호 우회는 사전 승인 없이 사용 금지

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# gh pr merge 명령인지 확인
if ! echo "$COMMAND" | grep -qE "gh pr merge"; then
  exit 0
fi

# --admin 플래그 감지
if echo "$COMMAND" | grep -q "\-\-admin"; then
  echo "⛔ --admin으로 브랜치 보호를 우회할 수 없습니다." >&2
  echo "" >&2
  echo "--admin은 브랜치 보호 규칙을 강제 통과시킵니다." >&2
  echo "사용이 필요하다면 먼저 사용자에게 사유를 설명하고 승인을 받으세요." >&2
  exit 2
fi

exit 0
