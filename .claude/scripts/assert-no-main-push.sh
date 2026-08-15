#!/usr/bin/env bash
# git push → main 직접 push 차단
# Claude가 실수로 main에 push하지 않도록. 사람의 수동 push는 영향 없음.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# heredoc 본문은 데이터다 — 이 가드를 **설명하는** 커밋 메시지·문서가 차단되면 안 된다.
# 08-12에 형제 `assert-no-admin.sh`만 고치고 여기를 빠뜨렸다가 08-15에 재발을 확인했다
# (오탐을 테스트하려던 명령 자체가 이 가드에 막히는 것으로 즉석 증명됐다).
# 사본이 갈라져 생긴 문제라 로직을 lib/로 뺐다 — 상세는 그 파일 주석.
. "$(dirname "${BASH_SOURCE[0]}")/lib/strip-heredoc.sh"
COMMAND=$(strip_heredoc "$COMMAND")

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
