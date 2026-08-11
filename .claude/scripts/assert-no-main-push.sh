#!/usr/bin/env bash
# git push → main 직접 push 차단
# Claude가 실수로 main에 push하지 않도록. 사람의 수동 push는 영향 없음.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# git push 명령인지 확인
if ! echo "$COMMAND" | grep -q "git push"; then
  exit 0
fi

# main 브랜치로 push 시도 감지
# 패턴: git push origin main, git push --force origin main, git push -f origin main 등
if echo "$COMMAND" | grep -qE "git push.*(origin\s+main|origin/main|\s+main$)"; then
  echo "⛔ main 브랜치에 직접 push할 수 없습니다." >&2
  echo "" >&2
  echo "feature 브랜치에서 PR을 통해 머지하세요:" >&2
  echo "  git push origin <branch-name>" >&2
  echo "  gh pr create --base main" >&2
  exit 2
fi

exit 0
