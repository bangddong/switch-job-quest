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

# 설계·계획 문서를 추가한다 (2026-08-06).
# 여기가 사각지대였다: 설계 스펙이 "daily=자체 스키마"를 🔴확정(07-20)해놓고 코드가 0줄인 상태로
# 17일간 CI를 통과했다. 결정이 사는 곳이 감시 밖이면 무결성 검사의 의미가 없다.
for _f in docs/superpowers/specs/*.md docs/superpowers/plans/*.md; do
  [ -f "$_f" ] && DOCS+=("$_f")
done

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
    # 📌 **D-** 로 시작하는 것만 결정 메타 줄이다.
    # 📌 자체는 이 레포에서 일반 강조로도 쓰인다(예: phase02 계획서의 "prod는 지금 200을 준다").
    # 좁히지 않으면 감시 대상을 넓히는 순간 그런 줄들이 전부 "ID 없음"으로 오탐된다.
    grep -nE '^>[[:space:]]*📌[[:space:]]*\*\*D-' "$f" 2>/dev/null | while IFS=: read -r n rest; do
      printf '%s\t%s\t%s\n' "$f" "$n" "$rest"
    done
  done
)

# ── D: 주장-검증 마커 ──────────────────────────────────────────────
# 형식 ①: <!-- verify: path/to/file -->              → 경로가 실재하는가
# 형식 ②: <!-- verify: path/to/file ~ 정규식 -->      → 실재 + **내용이 정규식과 일치**하는가
#
# 🔴 왜 ②가 필요한가 (2026-08-06 추가):
#   ①만 있으면 "존재 ≠ 구현"을 못 잡는다. 실제 사례 — 설계가 "daily=자체 스키마"를 🔴확정(07-20)
#   했는데 `FlywayConfig.kt`는 **존재하지만** `schemas()` 호출이 0건이었다. 경로 검사만으로는
#   17일간 초록이었다. 이건 이 레포에서 반복된 형태다: 검사가 주장보다 헐거우면,
#   더 쉽게 충족되는 대리 지표(존재/문자열 매칭/객체 동일성)를 통과시키고 진짜 성질은 놓친다.
#   ②는 "무엇이 참이어야 하는가"를 마커에 적게 해서 주장과 검사를 같은 층에 놓는다.
while IFS=: read -r f n rest; do
  [ -z "${f:-}" ] && continue
  payload=$(printf '%s' "$rest" | sed -n 's/.*<!--[[:space:]]*verify:[[:space:]]*\(.*\)-->.*/\1/p')
  [ -z "$payload" ] && continue

  # ` ~ ` 로 경로와 정규식을 가른다 (경로에 공백을 쓰지 않는 것이 이 레포 관례).
  case "$payload" in
    *" ~ "*)
      path="${payload%% ~ *}"
      pattern="${payload#* ~ }"
      ;;
    *)
      path="$payload"
      pattern=""
      ;;
  esac
  # 앞뒤 공백 제거
  path="${path#"${path%%[![:space:]]*}"}"; path="${path%"${path##*[![:space:]]}"}"
  pattern="${pattern#"${pattern%%[![:space:]]*}"}"; pattern="${pattern%"${pattern##*[![:space:]]}"}"
  [ -z "$path" ] && continue

  # 문법을 설명하는 **예시**는 건너뛴다 — 꺾쇠로 감싼 자리표시자(`<경로>`)가 규약.
  # (검사기 도입 당시 이 예외가 없어, 마커를 설명하는 문서 자체가 검사에 걸렸다.)
  case "$path" in
    *"<"*|*">"*) continue ;;
  esac

  if [ ! -e "$path" ]; then
    err "$f:$n verify 마커의 경로가 없다: $path"
  elif [ -n "$pattern" ]; then
    if [ ! -f "$path" ]; then
      err "$f:$n verify 내용 단언은 파일에만 쓸 수 있다 (디렉토리): $path"
    elif ! grep -qE -- "$pattern" "$path" 2>/dev/null; then
      err "$f:$n verify 내용 단언 실패 — '$path' 안에 없다: /$pattern/"
    fi
  fi
done < <(grep -rnE '<!--[[:space:]]*verify:' --include='*.md' .claude docs infra k8s CLAUDE.md 2>/dev/null)

if [ "$FAIL" -eq 0 ]; then
  echo "✅ 설계 무결성 통과 (결정 ${#SEEN_IDS[@]}건 검사)"
else
  echo "❌ 설계 무결성 위반 — 위 항목을 고치거나, 결정이 정말 바뀌었다면 메타 줄을 갱신할 것"
  echo "   절차: .claude/docs/design-change-procedure.md"
fi
exit "$FAIL"
