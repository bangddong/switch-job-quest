#!/usr/bin/env bash
# main 브랜치에서 프로젝트 파일 수정 차단
# Write/Edit PreToolUse hook — exit 2로 Claude에게 브랜치 생성을 강제함

# stdin에서 tool input JSON 읽기 (Claude Code 훅 프로토콜)
INPUT=$(cat)

FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty' 2>/dev/null || echo "")

# git 레포 외부 경로 (메모리, 홈 디렉토리 등)는 브랜치와 무관 → 허용
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo "")
if [ -n "$REPO_ROOT" ] && [ -n "$FILE_PATH" ]; then
  # 절대경로 또는 상대경로 모두 처리: 절대경로면 REPO_ROOT prefix 체크, 상대경로면 항상 레포 내부로 간주
  if [[ "$FILE_PATH" == /* ]] && [[ "$FILE_PATH" != "$REPO_ROOT"* ]]; then
    exit 0
  fi
elif [ -z "$REPO_ROOT" ]; then
  exit 0
fi

BRANCH=$(git branch --show-current 2>/dev/null)

# .claude/ 메타 파일은 main에서 직접 업데이트 허용 (CONTEXT.md, TASKS.md 등)
if [ "$BRANCH" = "main" ] && echo "$FILE_PATH" | grep -qE "(^|/|^$REPO_ROOT/)\.claude/"; then
  exit 0
fi

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
