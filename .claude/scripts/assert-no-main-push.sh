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

# git push 명령인지 확인 — `git -C <dir> push` · `git -c k=v push` 형태도 포함한다
# (study-log 발행 절차가 `git -C` 를 쓴다. 리터럴 "git push" 만 보면 그 형태가 통째로 빠진다).
PUSH_RE='git([[:space:]]+-[cC][[:space:]]+[^[:space:];&|]+)*[[:space:]]+push'
if ! echo "$COMMAND" | grep -qE "$PUSH_RE"; then
  exit 0
fi

# ── 히스토리 재작성(force push) 차단 — **모든 브랜치** (2026-10-17) ──
#
# 🔴 그전엔 이 가드가 *"push 대상이 main 인가"* 만 봤다. `--force` 는 main 패턴의 일부로만
#    나왔고, 작업 브랜치 force push 는 **어떤 훅에도 걸리지 않았다.** CLAUDE.md 의
#    *"히스토리 재작성(force push)도 금지된 우회로 취급"* 은 산문 규칙뿐이었다.
#    2026-10-16 실제 발생: 머지 뒤 작업 브랜치를 rebase → `--force-with-lease` 로 push.
#    맞는 길은 `git merge origin/main`(히스토리 보존)이었다. 레포 반복 실패 형태
#    *"산문 규칙은 밀리고 기계가 강제한 것만 지켜진다"* 의 또 한 사례.
#
# 잡는 형태 (한 세그먼트 `[^;&|]*` 안에서만 — L-29 오탐 교훈):
#   --force · --force-with-lease[=…] · --force-if-includes
#   -f 와 그것이 묶인 단축 플래그(-uf, -fu …)
#   +refspec (`origin +feat/x`, `+HEAD:feat/x`) — 플래그 없이 강제 갱신하는 문법
# ⚠️ `--follow-tags` 처럼 f 로 시작하는 긴 옵션, 브랜치명 안의 `-f`·`force` 는 오탐 케이스로 고정.
FORCE_RE="${PUSH_RE}[^;&|]*[[:space:]](--force(-with-lease|-if-includes)?(=[^[:space:]]*)?([[:space:]]|\$)|-[a-zA-Z]*f[a-zA-Z]*([[:space:]]|\$)|\+[^[:space:]])"
if echo "$COMMAND" | grep -qE "$FORCE_RE"; then
  echo "⛔ force push(히스토리 재작성)는 브랜치와 무관하게 금지입니다." >&2
  echo "" >&2
  echo "main 을 따라잡아야 하면 rebase 대신 merge 하세요:" >&2
  echo "  git fetch origin main && git merge origin/main && git push" >&2
  echo "이미 rebase 했다면: git reset --keep origin/<branch> 후 merge 로 다시 맞추세요." >&2
  exit 2
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
if echo "$COMMAND" | grep -qE "${PUSH_RE}[^;&|]*(origin[[:space:]]+main|origin/main|[[:space:]]+main\$)"; then
  echo "⛔ main 브랜치에 직접 push할 수 없습니다." >&2
  echo "" >&2
  echo "feature 브랜치에서 PR을 통해 머지하세요:" >&2
  echo "  git push origin <branch-name>" >&2
  echo "  gh pr create --base main" >&2
  exit 2
fi

exit 0
