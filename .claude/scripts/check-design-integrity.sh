#!/usr/bin/env bash
# 설계 기록의 무결성을 기계적으로 검사한다.
#
# 왜 필요한가 (07-29 전수 점검에서 드러난 것):
#   결정은 CONTEXT.md에, 재판정은 eks-migration-log.md에 따로 산다. 일지는 append-only 시계열이라
#   "지금 유효한 결정"을 알려주지 않는다. 그래서 CONTEXT만 읽은 사람은 **이미 뒤집힌 결정을
#   유효한 것으로 읽는다.** 실제로 "RDS 재탈락" 결정이 07-28에 재판정됐는데 CONTEXT에는 반영이
#   없어, 07-29에 그걸 근거로 잘못된 답을 했다.
#
# 검사 항목:
#   A. 결정 메타(📌) 줄의 `영향` 경로가 실재하는가        ← 코드 지웠는데 문서가 남는 것 차단
#   B. `상태` 값이 허용된 것인가 / ID가 유일한가
#   C. 부분무효·폐기 상태인데 `재판정` 링크가 없는가       ← "왜 뒤집혔는지" 추적 불가 차단
#   D. `<!-- verify: <path> -->` 마커의 경로가 실재하는가  ← "코드로 처리된다"는 주장 검증
#
# 사용: bash .claude/scripts/check-design-integrity.sh
# 종료코드: 0=통과, 1=위반

set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

FAIL=0
err() { echo "::error::$*"; FAIL=1; }
warn() { echo "::warning::$*"; }

# 검사 대상 문서
DOCS=(.claude/CONTEXT.md .claude/TASKS.md infra/aws-eks/README.md
      docs/eks-tutorial-steps.md docs/eks-session-sop.md CLAUDE.md k8s/README.md)

echo "── 설계 무결성 검사 ──"

# ── A/B/C: 결정 메타 줄 ────────────────────────────────────────────
# 형식: > 📌 **D-007** · 상태 `✅유효` · 영향 `path1`, `path2` · 재판정 `...`
ALLOWED_STATES="✅유효 🔄부분무효 ❌폐기 🚧진행중"
declare -a SEEN_IDS=()

while IFS=$'\t' read -r file lineno content; do
  [ -z "${file:-}" ] && continue

  id=$(printf '%s' "$content" | sed -n 's/.*\*\*\(D-[0-9][0-9]*\)\*\*.*/\1/p')
  if [ -z "$id" ]; then
    err "$file:$lineno 결정 메타 줄에 ID(**D-NNN**)가 없다: $content"
    continue
  fi

  # ID 중복
  for prev in ${SEEN_IDS[@]+"${SEEN_IDS[@]}"}; do
    [ "$prev" = "$id" ] && err "$file:$lineno 결정 ID 중복: $id"
  done
  SEEN_IDS+=("$id")

  # 상태
  state=$(printf '%s' "$content" | sed -n 's/.*상태 `\([^`]*\)`.*/\1/p')
  if [ -z "$state" ]; then
    err "$file:$lineno $id 에 상태가 없다"
  elif ! printf '%s' "$ALLOWED_STATES" | grep -qF -- "$state"; then
    err "$file:$lineno $id 상태값이 허용 목록 밖: '$state' (허용: $ALLOWED_STATES)"
  fi

  # 영향 경로 실재 확인 — `영향` 이후 구간의 백틱 토큰만
  impact_seg=$(printf '%s' "$content" | sed -n 's/.*영향 \(.*\)/\1/p' | sed 's/·[[:space:]]*재판정.*//')
  if [ -z "$impact_seg" ]; then
    err "$file:$lineno $id 에 영향 범위가 없다 (영향 \`path\` 형식)"
  else
    # 🔴 파이프로 while에 넣지 말 것 — 서브셸이라 FAIL=1이 밖으로 안 나간다.
    #    (실제로 그렇게 짰다가 "에러는 찍히는데 exit 0"인 검사기가 나왔다. 통과했다고
    #     믿게 만드니 검사기가 없는 것보다 나쁘다. 반증 테스트로 잡았다.)
    impact_paths=$(printf '%s' "$impact_seg" | grep -oE '`[^`]+`' | tr -d '`')
    while IFS= read -r p; do
      [ -z "$p" ] && continue
      case "$p" in
        Stage*|stage*) continue ;;   # Stage 라벨은 경로가 아님
      esac
      [ -e "$p" ] || err "$file:$lineno $id 영향 경로가 없다: $p"
    done <<< "$impact_paths"
  fi

  # 부분무효/폐기인데 재판정 링크 없음
  case "$state" in
    "🔄부분무효"|"❌폐기")
      printf '%s' "$content" | grep -q "재판정" \
        || err "$file:$lineno $id 상태가 '$state'인데 재판정 근거가 없다 (재판정 \`...\` 필요)"
      ;;
  esac
done < <(
  for f in "${DOCS[@]}"; do
    [ -f "$f" ] || continue
    grep -nE '^>[[:space:]]*📌' "$f" 2>/dev/null | while IFS=: read -r n rest; do
      printf '%s\t%s\t%s\n' "$f" "$n" "$rest"
    done
  done
)

# ── D: 주장-검증 마커 ──────────────────────────────────────────────
# 형식: <!-- verify: path/to/file -->
# "X는 코드로 처리된다"류 주장 옆에 달아 두면, 그 코드가 사라질 때 CI가 잡는다.
while IFS=: read -r f n rest; do
  [ -z "${f:-}" ] && continue
  path=$(printf '%s' "$rest" | sed -n 's/.*<!--[[:space:]]*verify:[[:space:]]*\([^[:space:]]*\)[[:space:]]*-->.*/\1/p')
  [ -z "$path" ] && continue
  # 문법을 설명하는 **예시**는 건너뛴다 — 꺾쇠로 감싼 자리표시자(`<경로>`)가 규약.
  # (검사기 도입 당시 이 예외가 없어, 마커를 설명하는 문서 자체가 검사에 걸렸다.)
  case "$path" in
    *"<"*|*">"*) continue ;;
  esac
  [ -e "$path" ] || err "$f:$n verify 마커의 경로가 없다: $path"
done < <(grep -rnE '<!--[[:space:]]*verify:' --include='*.md' .claude docs infra k8s CLAUDE.md 2>/dev/null)

if [ "$FAIL" -eq 0 ]; then
  echo "✅ 설계 무결성 통과 (결정 ${#SEEN_IDS[@]}건 검사)"
else
  echo "❌ 설계 무결성 위반 — 위 항목을 고치거나, 결정이 정말 바뀌었다면 메타 줄을 갱신할 것"
  echo "   절차: .claude/docs/design-change-procedure.md"
fi
exit "$FAIL"
