#!/usr/bin/env bash
# orchestrator 전용: be/ fe/ 코드 파일 쓰기 차단

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty')

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

if echo "$FILE_PATH" | grep -qE "(^|/)(be|fe)/"; then
  echo "차단: orchestrator는 be/ 또는 fe/ 코드를 직접 수정할 수 없습니다. 해당 에이전트에 위임하세요. (${FILE_PATH})" >&2
  exit 2
fi

exit 0
