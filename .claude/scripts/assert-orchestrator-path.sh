#!/usr/bin/env bash
# orchestrator 전용: be/ fe/ 코드 파일 쓰기 차단 (역할 경계 — 코드는 builder에 위임한다)
#
# ══ 2026-08-14: 에이전트 판별 추가 — 이게 없어서 위임이 통째로 막혀 있었다 ══
#
# 증상: `claude --agent orchestrator` 세션에서 be-feature-builder를 스폰하면, 그 서브에이전트가
#       `be/**` 를 Edit할 때 **이 훅에** 막혔다. builder는 `assert-be-path.sh`(be/ 허용)와
#       이 훅(be/ 차단)을 **동시에** 받는다 — 부모 frontmatter 훅이 자식에게 상속되기 때문.
#       결과: BE/FE 코드 작업이 원천 불가.
#
# 왜 여태 안 보였나: 이 파일은 #92(04-22)부터 mode 100644 = **한 번도 실행되지 않았다.**
#       #377(08-11)이 100755로 되살렸고(blob 해시 32a8811 동일 — 내용은 4개월간 불변),
#       08-14가 그 이후 **첫 builder 스폰**이었다. 즉 #377이 만든 회귀다.
#       *"켜져 있는 것처럼 보이는 가드가 꺼진 것보다 나쁘다"* 의 반대편 —
#       **꺼져 있던 걸 켰더니 너무 넓었다.**
#
# ── 판별 방법 (문서화된 사양) ──
#   PreToolUse 훅 입력 JSON에 다음이 온다:
#     agent_type : 에이전트 이름 ("orchestrator", "be-feature-builder", ...)
#     agent_id   : 서브에이전트 UUID — **서브에이전트 안에서만 존재**(메인 스레드엔 없음)
#   https://code.claude.com/docs/en/hooks.md#agent-fields-in-hook-input
#
# ── 🔴 fail-closed로 짠 이유 (여기가 핵심) ──
#   순진한 구현은 `[ "$agent_type" != orchestrator ] && exit 0` 이다. 그러면 필드가 없을 때
#   (구버전 CLI · jq 부재 · 스키마 변경) **조용히 통과** = 가드가 사라진다.
#   이 레포가 반복해서 데인 형태라(08-11 mode 644 / 08-12 ECR SHA 빈 값 / 08-13 IRSA 에러 문자열),
#   **"서브에이전트라는 적극적 증거가 있을 때만" 면제**한다. 증거가 없으면 orchestrator로 간주해 막는다.
#   → 판별에 실패하면 위임이 막힐 뿐이고(시끄러운 실패), 가드가 사라지지는 않는다(조용한 실패 아님).

INPUT=$(cat)

FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty' 2>/dev/null)
[ -n "$FILE_PATH" ] || exit 0

# be/ fe/ 가 아니면 애초에 관심 없다
echo "$FILE_PATH" | grep -qE "(^|/)(be|fe)/" || exit 0

AGENT_ID=$(echo "$INPUT"   | jq -r '.agent_id   // empty' 2>/dev/null)
AGENT_TYPE=$(echo "$INPUT" | jq -r '.agent_type // empty' 2>/dev/null)

# 면제 조건: **둘 다** 있어야 한다 (서브에이전트라는 적극적 증거) + 그게 orchestrator가 아닐 것
if [ -n "$AGENT_ID" ] && [ -n "$AGENT_TYPE" ] && [ "$AGENT_TYPE" != "orchestrator" ]; then
  exit 0   # 다른 에이전트다 → 그쪽 자기 가드가 판단한다
fi

echo "차단: orchestrator는 be/ 또는 fe/ 코드를 직접 수정할 수 없습니다. 해당 에이전트에 위임하세요." >&2
echo "  경로: ${FILE_PATH}" >&2
if [ -z "$AGENT_ID" ] && [ -z "$AGENT_TYPE" ]; then
  echo "  (agent_id·agent_type이 입력에 없다 → 메인 스레드로 간주. 서브에이전트인데 이 메시지를 봤다면" >&2
  echo "   훅 입력 스키마가 바뀐 것이니 이 스크립트의 판별부를 갱신할 것 — 원장 L-20)" >&2
fi
exit 2
