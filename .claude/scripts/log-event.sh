#!/usr/bin/env bash
# .claude/scripts/log-event.sh <event_type> [agent_name]
# 모든 Claude Code 이벤트를 activity.jsonl에 기록. 항상 exit 0 (모니터링이 실행을 막지 않음)

EVENT_TYPE="${1:-unknown}"
AGENT="${2:-main}"
INPUT=$(cat)
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_DIR="$PROJECT_ROOT/.claude/logs"
mkdir -p "$LOG_DIR"

TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty' 2>/dev/null || echo "")
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty' 2>/dev/null || echo "")
CMD_SNIPPET=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null | head -c 120 || echo "")
AGENT_FROM_INPUT=$(echo "$INPUT" | jq -r '.agent_name // .agent_type // empty' 2>/dev/null || echo "")

# SubagentStart/Stop는 입력에서 에이전트 이름 추출
if [[ "$EVENT_TYPE" == "SubagentStart" || "$EVENT_TYPE" == "SubagentStop" ]]; then
  AGENT="${AGENT_FROM_INPUT:-$AGENT}"
fi

jq -nc \
  --arg ts "$TIMESTAMP" \
  --arg event "$EVENT_TYPE" \
  --arg agent "$AGENT" \
  --arg tool "$TOOL_NAME" \
  --arg file "$FILE_PATH" \
  --arg cmd "$CMD_SNIPPET" \
  '{ts: $ts, event: $event, agent: $agent, tool: $tool, file: $file, cmd: $cmd}' \
  >> "$LOG_DIR/activity.jsonl" 2>/dev/null || true

exit 0
