#!/usr/bin/env bash
# main 브랜치에서 프로젝트 파일 수정 차단
# Write/Edit PreToolUse hook — exit 2로 Claude에게 브랜치 생성을 강제함

# stdin에서 tool input JSON 읽기 (Claude Code 훅 프로토콜)
INPUT=$(cat)

if command -v jq &>/dev/null; then
  FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty' 2>/dev/null || echo "")
else
  FILE_PATH=$(echo "$INPUT" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('file_path') or d.get('tool_input',{}).get('path') or '')" 2>/dev/null || echo "")
fi

# Windows 절대경로(C:\... 또는 C:/...)를 Git Bash 스타일(/c/...)로 정규화
# 이후 로직이 Unix 경로 기준으로 동작하므로 선행 정규화가 필요
# 효과: .claude/ 예외 처리 및 REPO_ROOT 비교 모두 정상 동작
if [[ "$FILE_PATH" =~ ^[A-Za-z]:[\\/] ]]; then
  FILE_PATH="${FILE_PATH//\\//}"           # 역슬래시 → 슬래시
  DRIVE="${FILE_PATH:0:1}"                  # 드라이브 문자 추출
  FILE_PATH="/${DRIVE,,}${FILE_PATH:2}"    # E:/foo → /e/foo
fi

# git 레포 외부 경로 (메모리, 홈 디렉토리 등)는 브랜치와 무관 → 허용
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo "")
if [ -n "$REPO_ROOT" ] && [ -n "$FILE_PATH" ]; then
  # 절대경로이고 REPO_ROOT 아래가 아니면 허용
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
