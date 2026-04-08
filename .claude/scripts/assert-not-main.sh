#!/usr/bin/env bash
# main 브랜치에서 파일 수정 차단
# Write/Edit PreToolUse hook — exit 2로 Claude에게 브랜치 생성을 강제함

BRANCH=$(git -C "$(git rev-parse --show-toplevel 2>/dev/null || echo '.')" branch --show-current 2>/dev/null)

if [ "$BRANCH" = "main" ]; then
  echo "⛔ main 브랜치에서 파일을 수정할 수 없습니다." >&2
  echo "" >&2
  echo "작업 브랜치를 먼저 생성하세요:" >&2
  echo "  git fetch origin main" >&2
  echo "  git checkout -b <type>/<name> origin/main" >&2
  echo "" >&2
  echo "브랜치 타입: feat / fix / chore / docs / refactor" >&2
  exit 2
fi

exit 0
