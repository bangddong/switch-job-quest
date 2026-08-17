#!/usr/bin/env bash
# assert-qa-run.sh 의 HIGH 항소 경로 테스트.
# 실제 findings/원장을 건드리지 않도록 임시 파일로 바꿔치기하고 끝나면 원복한다.
set -uo pipefail
ROOT=$(git rev-parse --show-toplevel)

# 🔴 **항상 임시 브랜치에서 돈다.** 주변 브랜치명에 결과가 좌우되면 안 된다.
#
# 두 번 데었다:
#   08-16  CI는 detached HEAD로 체크아웃한다(actions/checkout, PR은 머지 커밋).
#          `git branch --show-current`가 빈 문자열 → 마커 경로가 `.claude/qa-cache/`
#          (디렉토리)가 되어 `[ ! -f ]`가 참 → 전부 차단. 통과 기대 3건이 CI에서만 실패.
#          → detached일 때만 임시 브랜치를 만드는 것으로 땜질했다.
#   08-17  그 땜질이 반쪽이었다. `assert-qa-run.sh`는 `chore/`·`docs/` 브랜치를 **면제**하는데,
#          비-detached면 실제 브랜치명을 쓰므로 `chore/*`에서 전 케이스가 면제로 exit 0 →
#          **차단을 기대한 6건이 거짓 실패**. 실측: chore/harness-trim 3/9, tmp/x 9/9.
#          CI(detached)도 `fix/*`도 초록이라 아무도 몰랐다.
#
# 교훈: 테스트가 **환경의 어떤 값을 읽는다면 그 값을 테스트가 소유**해야 한다.
#       "특정 상황에서만 격리"는 격리가 아니다.
ORIG_BRANCH=$(git branch --show-current)
ORIG_HEAD=$(git rev-parse HEAD)
TMPBR="qa-gate-test-$$"
git checkout -q -b "$TMPBR" || { echo "  ⚠️ 임시 브랜치 생성 실패 — 게이트 테스트 불가"; exit 1; }
BRANCH="$TMPBR"
restore_branch() {
  if [ -n "$ORIG_BRANCH" ]; then
    git checkout -q "$ORIG_BRANCH" 2>/dev/null || true
  else
    git checkout -q --detach "$ORIG_HEAD" 2>/dev/null || true
  fi
  git branch -D "$TMPBR" -q 2>/dev/null || true
}
trap restore_branch EXIT
FIND="$ROOT/.claude/qa-cache/$BRANCH.findings.md"
LEDG="$ROOT/.claude/review-ledger.md"
HOOK="$ROOT/.claude/scripts/assert-qa-run.sh"
MARK="$ROOT/.claude/qa-cache/$BRANCH"

# 🔴 `.claude/qa-cache/` 는 gitignore라 **새 체크아웃(CI·worktree)엔 존재하지 않는다.**
#    이게 없으면 마커 쓰기가 조용히 실패하고, 훅은 "QA 미실행"으로 차단한다
#    → 통과를 기대한 3건이 CI에서만 실패했다(08-16, detached HEAD 재현으로 확정).
mkdir -p "$(dirname "$FIND")" 2>/dev/null || true

cp "$FIND" /tmp/find.bak 2>/dev/null || true
cp "$LEDG" /tmp/ledg.bak
cp "$MARK" /tmp/mark.bak 2>/dev/null || true
git rev-parse HEAD > "$MARK"

P=0; F=0
run() {  # $1=기대 $2=설명
  local got
  got=$(printf '{"tool_input":{"command":"gh pr create --base main"}}' | bash "$HOOK" >/dev/null 2>&1; echo $?)
  if [ "$got" = "$1" ]; then P=$((P+1)); printf '  ok   exp=%s got=%s  %s\n' "$1" "$got" "$2"
  else F=$((F+1)); printf '  FAIL exp=%s got=%s  %s\n' "$1" "$got" "$2"; fi
}

hdr='| ID | 등급 | 상태 | 위치 | 내용 |
|----|------|------|------|------|'

# ① HIGH + fixed → 통과
printf '%s\n| F-1 | HIGH | fixed | x | y |\n' "$hdr" > "$FIND"
run 0 "HIGH + fixed"

# ② HIGH + deferred → 차단 (항소 마커 없음)
printf '%s\n| F-1 | HIGH | deferred | x | y |\n' "$hdr" > "$FIND"
printf '\n| L-99 | `%s/F-1` | HIGH | 내용 | 근거 |\n' "$BRANCH" >> "$LEDG"
run 2 "HIGH + deferred (원장 있어도 차단)"

# ③ HIGH + wontfix + 원장 있으나 마커 없음 → 차단
printf '%s\n| F-1 | HIGH | wontfix | x | y |\n' "$hdr" > "$FIND"
run 2 "HIGH + wontfix + 마커 없음"

# ④ HIGH + wontfix + 마커 있음 → 통과
cp /tmp/ledg.bak "$LEDG"
printf '\n| L-99 | `%s/F-1` | HIGH | 내용 | 사용자 결정 근거 <!-- USER-DECIDED --> |\n' "$BRANCH" >> "$LEDG"
run 0 "HIGH + wontfix + 사용자 결정 마커"

# ⑤ 마커가 **다른 ID** 행에 있으면 통과되면 안 된다
cp /tmp/ledg.bak "$LEDG"
printf '\n| L-98 | `%s/F-9` | HIGH | 무관 | <!-- USER-DECIDED --> |\n' "$BRANCH" >> "$LEDG"
run 2 "마커가 다른 ID 행에 있음 → 차단"

# ⑤-b F-12 회귀: 마커가 **접두어가 겹치는 다른 ID**(F-10) 행에만 있으면 F-1은 통과 불가
cp /tmp/ledg.bak "$LEDG"
printf '%s\n| F-1 | HIGH | wontfix | x | y |\n' "$hdr" > "$FIND"
printf '\n| L-97 | `%s/F-10` | HIGH | 무관 | <!-- USER-DECIDED --> |\n' "$BRANCH" >> "$LEDG"
run 2 "F-12: F-1이 F-10 마커에 편승 시도 → 차단"

# ⑤-c 반대로 정확히 그 ID면 통과해야 한다
cp /tmp/ledg.bak "$LEDG"
printf '\n| L-97 | `%s/F-1` | HIGH | 내용 | <!-- USER-DECIDED --> |\n' "$BRANCH" >> "$LEDG"
run 0 "F-12: 정확히 F-1 행의 마커 → 통과"

# ⑥ MEDIUM + deferred + 원장 없음 → 차단 (규칙 3 회귀)
cp /tmp/ledg.bak "$LEDG"
printf '%s\n| F-2 | MEDIUM | deferred | x | y |\n' "$hdr" > "$FIND"
run 2 "MEDIUM deferred + 원장 미등재"

# ⑦ open 잔존 → 차단 (규칙 1 회귀)
printf '%s\n| F-3 | LOW | open | x | y |\n' "$hdr" > "$FIND"
run 2 "LOW open 잔존"

cp /tmp/find.bak "$FIND" 2>/dev/null || rm -f "$FIND"
cp /tmp/ledg.bak "$LEDG"
cp /tmp/mark.bak "$MARK" 2>/dev/null || rm -f "$MARK"
rm -f /tmp/find.bak /tmp/ledg.bak /tmp/mark.bak
echo "─────"; echo "통과 $P / 실패 $F"
