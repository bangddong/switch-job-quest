#!/usr/bin/env bash
# 브랜치 보호를 우회하거나 **약화**하는 명령을 차단한다.
#
# 2026-08-12 확장. 그전엔 `gh pr merge` + `--admin` 조합만 봤다(2줄짜리 grep).
#   그 상태의 구멍: `enforce_admins: false`라 관리자는 어차피 빨간 PR을 머지할 수 있고,
#   `--admin` 플래그는 gh의 편의 기능일 뿐이다. 다음 두 경로가 통째로 열려 있었다.
#     ① gh api --method PUT .../pulls/N/merge      → PR 머지 REST 직접 호출
#     ② gh api --method PUT .../branches/*/protection → **규칙 자체를 약화**
#   ②가 더 나쁘다. 가드를 우회하는 게 아니라 가드를 지우는 것이고, 흔적이 레포에 안 남는다
#   (required_status_checks는 GitHub 설정에만 있다 — session-status.sh의 "보호" 절 참고).
#
# 🔴 이 훅은 **에이전트를 막는 것**이지 사용자를 막는 것이 아니다.
#    사용자가 직접 터미널에서 실행하는 건 정상 경로다. 필요하면 사유를 설명하고 사용자에게 넘긴다.
#
# 통과시켜야 하는 것 (오탐 금지):
#   - `gh api .../protection` **조회**(GET) — session-status.sh가 매 세션 시작에 쓴다.
#   - `gh pr merge` (플래그 없음) — 보호 규칙을 정상 통과하는 머지.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

[ -n "$COMMAND" ] || exit 0

# ── heredoc 본문은 검사 대상이 아니다 ────────────────────────────
# 이 레포는 자기 가드에 **대한 문서·커밋 메시지**를 끊임없이 쓴다. 그 텍스트에
# `gh pr merge --admin` 같은 문자열이 들어가면 데이터인데도 명령으로 오인된다.
# 실제로 이 훅을 확장한 커밋의 메시지가 바로 이 훅에 막혔다(08-12).
# 오탐이 잦은 가드는 우회당하고, 우회는 가드를 침식한다 → 본문을 걷어내고 본다.
# 🔴 **fail-closed로 읽는다.** 초판은 그냥 `. lib` 이었는데, lib이 없거나 읽기에 실패하면
#    `strip_heredoc`이 미정의 → `COMMAND=$(strip_heredoc ...)` 가 **빈 문자열**이 되고
#    이후 모든 grep이 매치 실패해 **가드가 통째로 통과**했다(08-15 QA F-2, 실측 확인).
#    사본 분기를 막으려고 lib으로 뺀 것이 새 fail-open을 만든 셈이다.
LIB="$(dirname "${BASH_SOURCE[0]}")/lib/strip-heredoc.sh"
# shellcheck source=/dev/null
. "$LIB" 2>/dev/null
if ! declare -f strip_heredoc >/dev/null 2>&1; then
  echo "차단: 가드 헬퍼를 읽을 수 없습니다 ($LIB)" >&2
  echo "  판단 불가 상태에서 통과시키면 가드가 조용히 사라집니다 — 막는 쪽을 택합니다." >&2
  exit 2
fi
COMMAND=$(strip_heredoc "$COMMAND")

deny() {
  echo "⛔ $1" >&2
  echo "" >&2
  echo "$2" >&2
  echo "" >&2
  echo "필요하다면 먼저 사용자에게 사유를 설명하고 승인을 받으세요 (사용자가 직접 실행)." >&2
  exit 2
}

# ── ① gh pr merge --admin ────────────────────────────────────────
if echo "$COMMAND" | grep -qE 'gh +pr +merge' && echo "$COMMAND" | grep -q '\-\-admin'; then
  deny "--admin으로 브랜치 보호를 우회할 수 없습니다." \
       "--admin은 필수 상태 체크를 강제 통과시킵니다."
fi

# ── gh api 호출만 이하 검사 ──────────────────────────────────────
echo "$COMMAND" | grep -qE 'gh +api' || exit 0

# 쓰기 요청인가? (-X/--method 로 명시했거나, -f/-F/--input 이 있으면 gh가 POST로 보낸다)
IS_WRITE=0
echo "$COMMAND" | grep -qE '(\-X|--method) *(PUT|POST|PATCH|DELETE)' && IS_WRITE=1
echo "$COMMAND" | grep -qE '(^| )(-f|-F|--input|--field|--raw-field)( |=)' && IS_WRITE=1

# ── ② 브랜치 보호 규칙 자체를 쓰기 ───────────────────────────────
if [ "$IS_WRITE" -eq 1 ] && echo "$COMMAND" | grep -qE 'branches/[^ ]+/protection'; then
  deny "브랜치 보호 규칙을 에이전트가 변경할 수 없습니다." \
       "이건 가드 우회가 아니라 **가드 삭제**입니다. 필수 체크 목록은 GitHub 설정에만 있어
레포 이력에 흔적이 남지 않습니다 — 조용히 약화되면 아무도 모릅니다.
(조회(GET)는 허용됩니다: session-status.sh가 매 세션 드리프트를 검사합니다.)"
fi

# ── ③ PR 머지 REST 직접 호출 ─────────────────────────────────────
if echo "$COMMAND" | grep -qE 'pulls/[0-9]+/merge'; then
  deny "REST로 PR을 직접 머지할 수 없습니다." \
       "gh api .../pulls/N/merge 는 gh pr merge 의 가드를 건너뜁니다. 정상 경로는 gh pr merge 입니다."
fi

exit 0
