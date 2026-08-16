#!/usr/bin/env bash
# 명령 문자열에서 **heredoc 본문을 제거**한다. 훅 가드가 공유한다.
#
# ══ 왜 공유 파일인가 (2026-08-15) ══
#
# 08-12에 `assert-no-admin.sh`가 오탐을 냈다 — 그 가드를 **설명하는** 커밋 메시지가
# 명령으로 오인돼 차단됐다. 고치면서 heredoc 제거 로직을 그 파일에 **인라인으로** 넣었다.
#
# 그런데 형제인 `assert-no-main-push.sh`에는 안 넣었다. 08-15에 확인해보니 여전히
# 오탐을 낸다 — `git push origin main`을 **인용하는** 커밋 메시지가 차단된다.
# (그 오탐을 테스트하려던 명령 자체가 오탐에 걸리는 것으로 즉석 증명됐다.)
#
# 🔴 **"한쪽만 고치고 형제를 빠뜨린다"는 이 레포의 반복 패턴이다.**
#   - SOP §2b는 ECR에 `[ -z "$SHA" ]` 가드를 붙였는데 ESO엔 없었다 → 08-13에 당함
#   - L-3(로거 섀도잉)도 ai-api만 지적됐고 db-core의 동일 패턴은 빠져 있었다
#   → 사본이 갈라질 수 있으면 사본을 만들지 않는다. **여기 한 곳만 고치면 전부 따라온다.**
#
# ── 왜 heredoc 본문이 명령이 아닌가 ──
# 이 레포는 자기 가드에 **대한** 문서·커밋 메시지를 끊임없이 쓴다. 그 텍스트에
# `git push origin main`이나 `gh pr merge --admin` 같은 문자열이 들어가면 데이터인데도
# 명령으로 오인된다. 오탐이 잦은 가드는 우회당하고, 우회는 가드를 침식한다.
#
# ⚠️ **heredoc이 닫힌 뒤의 명령은 그대로 남긴다** — 면제가 "heredoc 뒤에 숨기기"라는
#    새 우회로가 되면 안 된다. 이건 테스트로 고정돼 있다(`test-guards.sh`).
#
# 사용법:
#   . "$(dirname "${BASH_SOURCE[0]}")/lib/strip-heredoc.sh"
#   COMMAND=$(strip_heredoc "$COMMAND")

strip_heredoc() {
  printf '%s\n' "$1" | awk '
    {
      if (skip) { if ($0 ~ ("^[[:space:]]*" tag "[[:space:]]*$")) skip=0; next }
      line=$0
      if (match(line, /<<-?[[:space:]]*['"'"'"]?[A-Za-z_][A-Za-z0-9_]*['"'"'"]?/)) {
        t=substr(line, RSTART, RLENGTH)
        gsub(/^<<-?[[:space:]]*|['"'"'"]/, "", t)
        tag=t; skip=1
      }
      print line
    }'
}
