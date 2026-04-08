#!/usr/bin/env bash
# be-feature-builder 전용: fe/ 경로 쓰기 차단

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty')

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

if echo "$FILE_PATH" | grep -qE "(^|/)fe/"; then
  echo "차단: be-feature-builder는 fe/ 경로를 수정할 수 없습니다. (${FILE_PATH})" >&2
  exit 2
fi

exit 0
