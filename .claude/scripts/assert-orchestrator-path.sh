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
#   (구버전 CLI · 스키마 변경) **조용히 통과** = 가드가 사라진다.
#   이 레포가 반복해서 데인 형태라(08-11 mode 644 / 08-12 ECR SHA 빈 값 / 08-13 IRSA 에러 문자열),
#   **"서브에이전트라는 적극적 증거가 있을 때만" 면제**한다. 증거가 없으면 orchestrator로 간주해 막는다.
#   → 판별에 실패하면 위임이 막힐 뿐이고(시끄러운 실패), 가드가 사라지지는 않는다(조용한 실패 아님).
#
#   ⚠️ **2026-08-14 QA F-3 — 이 주석의 초판은 거짓이었다.** *"jq 부재도 fail-closed가 막는다"* 고
#      적었는데, jq가 없으면 **FILE_PATH 파싱이 먼저 빈 값이 되어 `exit 0`** — 판별부에 닿기도 전에
#      가드 전체가 통과했다. 없는 방어를 있다고 주장한 것이고, 하필 *"조용한 통과를 막는다"* 를
#      설명하는 주석에서 그랬다. → 아래 파서 선택부(jq → python3 → 둘 다 없으면 `exit 2`)로
#      **실제로** 막고, 주석을 사실에 맞췄다.
#
#   ⚠️ **2026-08-14 QA F-5 — 그 수정이 같은 병을 새 코드에 다시 심었다.** 폴백을 범용
#      "JSON 경로 미니언어"(`.a.b // .c.d`를 파싱)로 짰는데, `lstrip('.')`이 **문자열 맨 앞에서만**
#      점을 지워 **두 번째 대안이 영영 매칭되지 않았다**. 실측: jq 없는 환경에서
#      `{"tool_input":{"path":"be/x.kt"}}` → `exit 0` = **가드 소멸**(`.file_path`는 정상 차단).
#      → **미니언어를 없앴다.** 필요한 값 3개를 파이썬에서 직접, 명시적으로 꺼낸다.
#      교훈: 가드 안에 **범용 파서를 만들지 마라.** 그 파서의 버그가 곧 가드의 구멍이다.
#
# ── 필드 실재 근거 (문서가 아니라 연역) ──
#   `agent_type`·`agent_id`는 문서에 있지만(hooks.md#agent-fields-in-hook-input) 페이로드를 직접
#   덤프하진 못했다(프로브가 권한 분류기에 차단됨). 대신 **행동으로 증명된다**:
#   `be/` 경로가 이 스크립트를 통과할 수 있는 분기는 **아래 면제 하나뿐**이고, 그것은 두 필드가
#   모두 비어있지 않아야 열린다. 수정 전 builder는 차단됐고 수정 후 통과해 실제로 커밋했다
#   → **PreToolUse 페이로드에 두 필드가 존재한다.**
#   ℹ️ 단 `log-event.sh:18`은 같은 목적에 `.agent_name // .agent_type`을 쓴다 — 레포 안에서 필드명
#      합의가 없다. 여기서는 위 증명이 있는 `agent_type`만 신뢰한다.

INPUT=$(cat)

# ── 파서가 없으면 판단할 수 없다 → 막는다 (fail-closed) ──
if command -v jq >/dev/null 2>&1; then
  FILE_PATH=$(echo  "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty' 2>/dev/null)
  AGENT_ID=$(echo   "$INPUT" | jq -r '.agent_id   // empty' 2>/dev/null)
  AGENT_TYPE=$(echo "$INPUT" | jq -r '.agent_type // empty' 2>/dev/null)
elif command -v python3 >/dev/null 2>&1; then
  # 파이썬 소스는 **작은따옴표**로 감싼다 — 셸 보간이 전혀 일어나지 않게(따옴표 층에서 나는 사고 차단).
  # 찾을 키는 인자로 넘긴다. 경로 문법을 해석하지 않고 **필요한 것만 직접** 꺼낸다(QA F-5).
  pyget() {
    echo "$INPUT" | python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(0)
if not isinstance(d, dict):
    sys.exit(0)
want = sys.argv[1]

# jq의 `//` 는 **null·false만** falsy로 본다. 파이썬 `or` 는 0·""·[] 까지 falsy라
# 같은 페이로드에서 두 경로가 갈린다(08-14 QA F-7 실측: {"agent_id":0} → jq "0" vs python "").
# 지금 스키마로는 도달 불가한 이론적 발산이지만, **두 경로가 다르게 판정하는 가드**는
# 그 자체로 신뢰할 수 없다 → jq 시맨틱에 맞춘다.
def pick(*vals):
    for v in vals:
        if v is not None and v is not False:
            return v
    return ""

if want == "path":
    ti = d.get("tool_input")
    ti = ti if isinstance(ti, dict) else {}
    v = pick(ti.get("file_path"), ti.get("path"))
else:
    v = pick(d.get(want))
print(v)
' "$1" 2>/dev/null
  }
  FILE_PATH=$(pyget path)
  AGENT_ID=$(pyget agent_id)
  AGENT_TYPE=$(pyget agent_type)
else
  echo "차단: jq·python3가 모두 없어 훅 입력을 해석할 수 없습니다." >&2
  echo "  판단 불가 상태에서 통과시키면 가드가 조용히 사라집니다 — 막는 쪽을 택합니다." >&2
  exit 2
fi

# ⚠️ **알려진 한계 (의도적, 08-14).** `file_path`가 **빈 문자열**이고 `path`에 실제 값이 있으면
#    jq `//` 시맨틱상 빈 문자열이 채택돼 여기서 통과한다. 고치지 않은 이유:
#    ①`file_path`가 **아예 없으면** `path`로 정상 폴백해 차단된다(실측 exit=2) — 도구는 둘 중
#      하나만 보내므로 현실 페이로드에서 구멍이 아니다 ②"경로 불명 → 차단 불가 → 통과"는
#      `assert-not-main.sh`가 이미 쓰는 레포 공통 관례다 ③이 파일은 3라운드 연속 버그를 냈고,
#      도달 불가한 경계를 위해 로직을 더 얹는 것은 *"3번 시도했는데 4번 더"* 다.
#    🔑 재검토 트리거: 훅 payload에서 `file_path`와 `path`가 **동시에** 관측되면.
[ -n "$FILE_PATH" ] || exit 0

# be/ fe/ 가 아니면 애초에 관심 없다
echo "$FILE_PATH" | grep -qE "(^|/)(be|fe)/" || exit 0

# 면제 조건: **둘 다** 있어야 한다 (서브에이전트라는 적극적 증거) + 그게 orchestrator가 아닐 것
if [ -n "$AGENT_ID" ] && [ -n "$AGENT_TYPE" ] && [ "$AGENT_TYPE" != "orchestrator" ]; then
  exit 0   # 다른 에이전트다 → 그쪽 자기 가드가 판단한다
fi

echo "차단: orchestrator는 be/ 또는 fe/ 코드를 직접 수정할 수 없습니다. 해당 에이전트에 위임하세요." >&2
echo "  경로: ${FILE_PATH}" >&2
# 진단 힌트는 **부분 드리프트**(한쪽만 소실)에서도 떠야 한다 — QA F-2.
# 정작 걱정한 시나리오에서 힌트가 안 뜨면 디버깅이 제일 어려운 때 아무 단서가 없다.
if [ "$AGENT_TYPE" != "orchestrator" ]; then
  echo "  진단: agent_id=$([ -n "$AGENT_ID" ] && echo 있음 || echo 없음) / agent_type='${AGENT_TYPE}'" >&2
  echo "  둘 다 없으면 메인 스레드다(정상). 서브에이전트인데 이 메시지를 봤다면 훅 입력 스키마가" >&2
  echo "  바뀐 것이니 위 판별부를 갱신할 것 — 원장 L-20 참조." >&2
fi
exit 2
