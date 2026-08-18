#!/usr/bin/env bash
# git push → main 직접 push 차단
# Claude가 실수로 main에 push하지 않도록. 사람의 수동 push는 영향 없음.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# heredoc 본문은 데이터다 — 이 가드를 **설명하는** 커밋 메시지·문서가 차단되면 안 된다.
# 08-12에 형제 `assert-no-admin.sh`만 고치고 여기를 빠뜨렸다가 08-15에 재발을 확인했다
# (오탐을 테스트하려던 명령 자체가 이 가드에 막히는 것으로 즉석 증명됐다).
# 사본이 갈라져 생긴 문제라 로직을 lib/로 뺐다 — 상세는 그 파일 주석.
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

# git push 명령인지 확인
if ! echo "$COMMAND" | grep -q "git push"; then
  exit 0
fi

# main 브랜치로 push 시도 감지
# 패턴: git push origin main, git push --force origin main, git push -f origin main 등
#
# 🔴 **`.*` 대신 `[^;&|]*` 를 쓴다 — 세그먼트를 넘지 않게 (2026-08-18, 원장 L-29).**
#
# 그리디 `.*` 는 **명령 구분자를 건너뛴다.** 그래서 이것이 차단됐다:
#     git push -q && git log --oneline origin/main..HEAD
#     ^^^^^^^^                          ^^^^^^^^^^^
# 앞은 현재 브랜치 push(정상), 뒤는 읽기 전용 조회인데 `.*` 가 `&&` 를 건너뛰어 이어 붙였다.
# 실제로 이 PR(#387)의 CONTEXT 갱신 커밋이 막혔고, **그 오탐을 진단하려던 명령도 막혔다.**
#
# ⚠️ 오탐이 잦은 가드는 우회당하고, 우회는 가드를 침식한다(L-21 에서 실제로 발생).
#    `[^;&|]*` 는 `;` `&&` `||` `|` 를 만나면 멈추므로 **한 세그먼트 안의 인자만** 본다.
#    차단력은 그대로다 — `git push origin main && ...` 는 첫 세그먼트에서 잡힌다(테스트로 고정).
if echo "$COMMAND" | grep -qE "git push[^;&|]*(origin[[:space:]]+main|origin/main|[[:space:]]+main\$)"; then
  echo "⛔ main 브랜치에 직접 push할 수 없습니다." >&2
  echo "" >&2
  echo "feature 브랜치에서 PR을 통해 머지하세요:" >&2
  echo "  git push origin <branch-name>" >&2
  echo "  gh pr create --base main" >&2
  exit 2
fi

exit 0
