#!/usr/bin/env bash
# qa-reviewer가 **실제로** 파일을 고쳤는지 본다. 명령을 해석하지 않고 **결과**를 본다.
#
# 사용:  qa-effect-guard.sh snapshot   (SubagentStart)
#        qa-effect-guard.sh verify     (SubagentStop)
#
# ══ 왜 이게 필요한가 — 정적 검사의 구조적 한계 (2026-08-15) ══
#
# `assert-qa-readonly.sh`는 명령 문자열을 파싱해 허용목록으로 막는다. 3라운드에 걸쳐 조였고
# **매 라운드 새 우회가 열렸다**:
#   1차  따옴표 미인식 → 정상 명령 오탐 (원장 L-21)
#   2차  겹따옴표 안 `$(...)` 가려짐 → `mkdir -p "$(rm -rf be/core)"`
#   3차  ANSI-C 인용 `$'x\'y'` → 뒤쪽 명령이 통째로 숨음 (QA F-1)
#   4차  허용 명령의 **인자** 속 실행 지시 → `find -exec rm`, `xargs rm`,
#        `awk 'BEGIN{system("...")}'`, 프로세스 치환 `diff <(rm -rf be/)` (QA F-5·F-6)
#
# 🔴 이 레포는 같은 결론에 이미 도달한 적이 있다 — 원장 **L-12**:
#      *"정적 텍스트 검사의 구조적 한계이지 구현 결함이 아니다. 3라운드에 걸쳐 조였고 매번 새
#        우회가 열렸다. 정규식을 더 정교하게 만드는 것은 '3번 시도했는데 4번 더'에 해당한다.
#        대신 우회 불가능한 검사를 추가했다 — 최종 상태를 보므로 소스 문법과 무관하다."*
#
#   → **소스 문법이 아니라 결과를 본다.** 어떤 셸 트릭을 쓰든 파일이 바뀌면 여기 걸린다.
#
# ── 역할 분담 (둘 다 남긴다) ──
#   assert-qa-readonly.sh : **사전 차단 + 안내**. 정직한 실수(`echo > file`, `sed -i`,
#                           `git checkout`)를 그 자리에서 막고 *"보고하라"* 고 알려준다.
#                           우회 가능하지만, 막아야 할 현실적 실패는 여기서 다 잡힌다.
#   이 파일                : **사후 탐지**. 파싱을 안 하므로 우회가 원리적으로 불가능하다.
#                           대신 이미 일어난 뒤에 안다 → 그래서 둘 다 필요하다.
#
# ⚠️ **자동 되돌리기를 하지 않는다.** qa-reviewer는 orchestrator와 **같은 워킹트리**에서 돈다.
#    `git checkout --` 으로 되돌리면 orchestrator의 미커밋 작업을 파괴할 수 있다.
#    탐지·보고까지만 하고 판단은 사람/orchestrator에게 넘긴다.

set -uo pipefail
MODE="${1:-}"
ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$ROOT" || exit 0

INPUT=$(cat 2>/dev/null || echo '{}')
AGENT=$(echo "$INPUT" | jq -r '.agent_type // .agent_name // empty' 2>/dev/null)
# 🔑 `agent_id` 는 서브에이전트마다 고유하다. baseline에 새겨두면
#    "**이 에이전트에 대해** Start가 돌았는가"를 정확히 판정할 수 있다 (08-16 QA F-16).
AGENT_ID=$(echo "$INPUT" | jq -r '.agent_id // empty' 2>/dev/null)

# qa-reviewer가 아니면 관심 없다. (판별 불가 시엔 아무것도 하지 않는다 —
#  이 훅은 차단이 아니라 탐지이므로, 여기서 fail-closed로 막으면 무관한 에이전트를 방해한다.)
[ "$AGENT" = "qa-reviewer" ] || exit 0

# 🔴 **baseline을 `.claude/qa-cache/` 에 두면 안 된다** (08-15 QA F-8).
#    거기는 qa-reviewer가 **쓰기를 허용받은** 유일한 경로다(`assert-qa-readonly.sh`의 target_ok).
#    즉 감시자의 기준점이 **피감시자의 쓰기 영역 안**에 있었다 — 허용된 명령만으로
#    `git status … > .claude/qa-cache/.effect-baseline` 을 다시 만들면 델타가 0이 된다.
#    파서를 우회할 필요조차 없었다. 감시자는 피감시자가 손댈 수 없는 곳에 상태를 둬야 한다.
STATE_DIR="$ROOT/.claude/.effect-state"
BASELINE="$STATE_DIR/qa-reviewer"
mkdir -p "$STATE_DIR" 2>/dev/null || true

# 추적 파일의 변경 상태 + 내용 해시. qa-cache/ 자체는 제외(리뷰어의 정당한 작업 산출물).
# 파일별로 (상태, 내용해시) 한 줄씩. 델타를 보여주려면 목록 자체를 남겨야 한다 —
# 해시 하나만 저장하면 "뭔가 바뀌었다"까지만 알고 **무엇이** 바뀌었는지는 못 말한다
# (초판이 그래서 baseline 시점에 이미 수정돼 있던 파일까지 싸잡아 보고했다).
# 🔴 **gitignore된 고가치 파일은 `git status`가 못 본다** (08-15 QA F-10).
#    특히 `.claude/settings.local.json` 은 **권한 allow 규칙 343건**을 담고 있어,
#    거기 한 줄 쓰면 **세션을 넘어 지속되는 권한 상승**이 된다.
#    실수로 일어날 일은 아니지만(위협 모델상 범위 밖) **결과의 크기가 다르므로**
#    파서를 넓히는 대신 **고정 목록을 해시로 감시**한다 — 범용 규칙이 아니라 열거다.
#    ⚠️ 목록을 늘릴 때는 "여기 쓰이면 세션을 넘어 영향이 남는가"만 기준으로 삼을 것.
WATCH="
.claude/settings.local.json
.claude/settings.json
.claude/launch.json
"

snapshot_state() {
  git status --porcelain -- . ':(exclude).claude/qa-cache' 2>/dev/null | while read -r st f; do
    h=$( [ -f "$f" ] && shasum "$f" 2>/dev/null | awk '{print $1}' || echo "-" )
    printf '%s %s %s\n' "$st" "$h" "$f"
  done
  # git이 안 보는 것들 — 존재 여부와 내용해시를 직접 본다
  printf '%s\n' "$WATCH" | while read -r w; do
    [ -n "$w" ] || continue
    h=$( [ -f "$ROOT/$w" ] && shasum "$ROOT/$w" 2>/dev/null | awk '{print $1}' || echo "absent" )
    printf 'W %s %s\n' "$h" "$w"
  done
}

case "$MODE" in
  snapshot)
    rm -f "$BASELINE.done" 2>/dev/null || true   # 새 세션 = 새 검증 기회
    { printf 'AGENT %s\n' "${AGENT_ID:-unknown}"; snapshot_state; } > "$BASELINE" 2>/dev/null || true
    exit 0
    ;;
  verify)
    # 🔴 fail-closed (08-15 QA F-11). 여기까지 왔다는 건 이미 agent_type=qa-reviewer로
    #    확정된 뒤다 — 이 시점에 baseline이 없다는 것은 SubagentStart가 안 돌았거나
    #    세션 중 지워졌다는 신호다. 둘 다 "탐지가 작동하지 않았다"는 뜻이고,
    #    그걸 통과로 처리하면 이 PR이 F-2에서 배운 것과 정확히 모순된다.
    if [ ! -f "$BASELINE" ]; then
      echo "🔴 qa-reviewer 효과 검사의 baseline이 없다 — 탐지가 작동하지 않았다." >&2
      echo "   SubagentStart 훅이 안 돌았거나 세션 중 baseline이 지워졌다." >&2
      echo "   판단 불가 상태를 통과로 처리하지 않는다(가드가 조용히 사라지는 경로)." >&2
      echo "   확인: .claude/agents/qa-reviewer.md 의 SubagentStart 배선 + $BASELINE" >&2
      exit 2
    fi
    # 🔴 **baseline을 지우지 않는다** (08-16 QA F-15).
    #    초판은 verify에서 무조건 `rm -f` 했다. 그런데 **SubagentStop은 한 세션에서 여러 번
    #    발생한다**(하네스 알림에도 "the same task-id may notify more than once"라고 적혀 있다).
    #    → 1회차 verify가 통과하며 baseline을 지우고, Stop 훅 피드백으로 세션이 이어지면
    #      2회차 verify가 "baseline 없음"으로 **fail-closed(exit 2)** 해서 리뷰어를 차단했다.
    #    즉 **F-11에서 넣은 fail-closed가 자기 차단 루프**를 만들었다. 실측 피해:
    #      QA 3회 연속 무응답(169k·70k·130k 토큰) — 매 Stop마다 막혔기 때문이다.
    #    🔑 교훈: fail-closed로 바꿀 때는 **수명주기 가정**(Start:Stop = 1:1)을 먼저 확인해야 한다.
    #      "없으면 막는다"가 옳으려면 "있어야 할 때 반드시 있다"가 참이어야 한다.
    #
    #    대신 **소비 표시**를 남긴다: 이미 검증한 baseline이면 다음 Stop은 조용히 통과한다.
    #    (baseline이 **아예 없는** 경우의 fail-closed는 그대로 유지 — F-11의 의도는 살린다)
    # 🔴 **이 에이전트에 대해** Start가 돌았는지 확인한다 (08-16 QA F-16).
    #    `.done`만 보면, 다음 세션에 Start가 안 돌아도 **이전 세션의 .done이 남아** 조용히
    #    통과한다 — F-11의 fail-closed를 내가 되살린 `.done`으로 우회시킨 셈이었다.
    #    Start 미실행은 이 레포 실측 전례가 있다(#377, 훅 12개 mode 644).
    # 🔴 `agent_id` 부재도 fail-closed (08-16 QA F-17). 자매 가드
    #    (`assert-be-path.sh`·`assert-orchestrator-path.sh`)는 "증거 불충분 → 막는다"로
    #    이미 그렇게 하는데 이 파일만 예외였다 — **가드 간 일관성이 깨지면 그 틈이 구멍이 된다**
    #    (08-15 strip-heredoc 사본 분기가 정확히 그 형태였다).
    if [ -z "$AGENT_ID" ]; then
      echo "🔴 qa-reviewer 효과 검사: 훅 입력에 agent_id가 없다 — 소유권을 판정할 수 없다." >&2
      echo "   판단 불가를 통과로 처리하지 않는다. 훅 입력 스키마를 확인하라." >&2
      exit 2
    fi
    BASE_AGENT=$(awk '/^AGENT /{print $2; exit}' "$BASELINE" 2>/dev/null)
    if [ "$BASE_AGENT" != "$AGENT_ID" ]; then
      echo "🔴 qa-reviewer 효과 검사: baseline이 **다른 에이전트**의 것이다." >&2
      echo "   baseline=$BASE_AGENT / 현재=$AGENT_ID" >&2
      echo "   이 에이전트에 대해 SubagentStart가 돌지 않았다는 뜻이다 — 탐지가 작동하지 않았다." >&2
      echo "   확인: .claude/agents/qa-reviewer.md 의 SubagentStart 배선" >&2
      exit 2
    fi

    # 같은 에이전트의 반복 Stop이면 이미 검증했다 → 조용히 통과 (F-15)
    if [ -f "$BASELINE.done" ] && [ "$(cat "$BASELINE.done" 2>/dev/null)" = "$AGENT_ID" ]; then
      exit 0
    fi

    AFTER=$(snapshot_state)
    # ⚠️ `comm` 은 **양쪽이 정렬돼 있어야** 한다. AGENT 줄은 소유권 확인용이지 비교 대상이
    #    아니므로 양쪽에서 뺀 뒤 각각 정렬한다. (초판은 한쪽에만 AGENT 줄을 앞에 붙여
    #    전역 정렬이 깨졌고, 변경이 없는데도 델타가 나왔다 — 스위트가 잡았다)
    DELTA=$(comm -13 <(grep -v '^AGENT ' "$BASELINE" 2>/dev/null | sort) <(printf '%s\n' "$AFTER" | sort))
    printf '%s' "$AGENT_ID" > "$BASELINE.done" 2>/dev/null || true
    [ -z "$DELTA" ] && exit 0

    echo "🔴 qa-reviewer 세션 중 **추적 파일이 변경됐다**." >&2
    echo "" >&2
    echo "   리뷰어는 읽기 전용이어야 한다 — 리뷰 대상을 고치면 리뷰가 아니라 자기승인이다." >&2
    echo "   이 검사는 명령을 해석하지 않고 **결과**를 보므로 셸 트릭으로 우회되지 않는다." >&2
    echo "" >&2
    echo "   변경된 것:" >&2
    printf '%s\n' "$DELTA" | awk '{print "     " $1 " " $3}' >&2
    echo "" >&2
    echo "   ⚠️ 자동으로 되돌리지 않는다 — 같은 워킹트리의 orchestrator 작업을 파괴할 수 있다." >&2
    echo "      위 목록을 확인하고 의도한 변경인지 직접 판단할 것." >&2
    echo "      리뷰어가 낸 변경이라면 되돌리고, 왜 그랬는지 원장에 남길 것." >&2
    exit 2
    ;;
  *)
    echo "사용법: $(basename "$0") snapshot|verify" >&2
    exit 0
    ;;
esac
