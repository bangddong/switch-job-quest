#!/usr/bin/env bash
# be-feature-builder 전용: fe/ 경로 쓰기 차단

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty')

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

# ── 이 훅은 be-feature-builder 전용이다 (2026-08-15 추가) ──
# 부모 에이전트의 frontmatter 훅은 **서브에이전트에 상속된다**. 2026-08-14에
# `assert-orchestrator-path.sh`가 바로 그 이유로 builder를 통째로 막아 BE/FE 작업이
# 원천 불가였다(#383). 이 파일은 형제 에이전트라 지금은 도달 불가하지만 **같은 모양**이고,
# 그 버그는 "처음 발동할 때야 알게 되는" 종류였다 → 미리 자기 신원을 확인한다.
# fail-closed: 다른 에이전트라는 **적극적 증거**가 있을 때만 비켜준다.
AGENT_ID=$(echo "$INPUT" | jq -r '.agent_id // empty' 2>/dev/null)
AGENT_TYPE=$(echo "$INPUT" | jq -r '.agent_type // empty' 2>/dev/null)
if [ -n "$AGENT_ID" ] && [ -n "$AGENT_TYPE" ] && [ "$AGENT_TYPE" != "be-feature-builder" ]; then
  exit 0
fi

if echo "$FILE_PATH" | grep -qE "(^|/)fe/"; then
  echo "차단: be-feature-builder는 fe/ 경로를 수정할 수 없습니다. (${FILE_PATH})" >&2
  exit 2
fi

exit 0
