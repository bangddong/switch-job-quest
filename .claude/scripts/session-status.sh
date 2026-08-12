#!/usr/bin/env bash
# 세션 시작 대시보드 — "지금 나는 어디에 있나"를 **전부 실시간 조회**로 답한다.
#
# ── 왜 이 스크립트가 존재하나 ──
# 예전엔 이 정보를 `.claude/CONTEXT.md`의 "현재 상태" 표에 적어뒀다. 그런데 그 표는
# **PR 안에서 자기 PR의 상태를 적는** 구조라, 담을 수 있는 마지막 값이 "머지 대기"였다.
# 그 문장은 머지되는 순간 **반드시** 거짓이 된다 — 성실성으로는 못 이기는 자기참조다.
# 결과: CONTEXT를 고치는 것만이 목적인 "클린 클로즈" PR이 24건 누적됐다(2026-07-31 실측).
#
# → 파생 가능한 상태는 저장하지 않는다. 여기서 매번 새로 읽는다. 원리적으로 썩을 수 없다.
#   CONTEXT.md에는 **파생 불가능한 것만** 남긴다(결정·근거·다음 작업·기각 사유).
#
# 설계 규칙: 어떤 조회가 실패해도(오프라인·gh 미설치·자격증명 없음) 나머지는 계속 출력한다.
#           대시보드가 죽으면 사람이 안 쓰게 되고, 안 쓰는 대시보드는 없는 것과 같다.

set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

C_DIM='\033[2m'; C_B='\033[1m'; C_R='\033[31m'; C_Y='\033[33m'; C_G='\033[32m'; C_0='\033[0m'
hdr() { printf "\n${C_B}%s${C_0}\n" "$1"; }

# 한글은 터미널에서 2칸을 차지하는데 `printf %-Ns`는 **문자 수**로 센다 →
# 한글 라벨이 섞이면 열이 어긋난다. 표시 폭으로 직접 채운다.
# ⚠️ 문자 범위(`[[ $c == [\ -~] ]]`)로 ASCII를 판정하면 **안 된다** — bash의 대괄호 범위는
#    로케일 collation 순서를 따르므로 공백은 매칭되는데 대문자 P·R 등이 빠진다(실측).
#    그러면 "열린 PR"이 7칸 대신 9칸으로 계산돼 열이 어긋난다.
#    → 바이트 수로 센다. UTF-8에서 ASCII=1바이트/1칸, 한글=3바이트/2칸이므로
#      한글 개수 = (바이트수 - 문자수) / 2,  표시폭 = 문자수 + 한글 개수.
#    (이모지는 4바이트/2칸이라 이 식이 안 맞지만 라벨에 이모지를 쓰지 않는다.)
pad() {
  local s="$1" target="${2:-12}" chars bytes w
  chars=${#s}
  bytes=$(LC_ALL=C printf '%s' "$s" | wc -c)
  w=$((chars + (bytes - chars) / 2))
  printf '%s' "$s"
  while [ "$w" -lt "$target" ]; do printf ' '; w=$((w + 1)); done
}
row() { printf "  %s %b\n" "$(pad "$1" 12)" "$2"; }

printf "${C_B}━━ 세션 상태 ━━${C_0} ${C_DIM}(전부 실시간 조회 — 저장된 값 없음)${C_0}\n"

# ── git ──────────────────────────────────────────────────────────
hdr "git"
row "브랜치" "$(git branch --show-current 2>/dev/null || echo '?')"
DIRTY=$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')
if [ "${DIRTY:-0}" -gt 0 ]; then
  row "미커밋" "${C_Y}${DIRTY}개${C_0}"
else
  row "미커밋" "0개"
fi
row "최근 커밋" "$(git log --oneline -1 2>/dev/null || echo '?')"

# ── PR (gh 없으면 건너뜀) ─────────────────────────────────────────
hdr "PR"
if command -v gh >/dev/null 2>&1; then
  OPEN=$(gh pr list --state open --json number,title -q '.[] | "#\(.number) \(.title)"' 2>/dev/null)
  if [ -n "$OPEN" ]; then
    echo "$OPEN" | while IFS= read -r line; do row "열린 PR" "${C_Y}${line}${C_0}"; done
  else
    row "열린 PR" "0건"
  fi
  row "최근 머지" "$(gh pr list --state merged --limit 1 --json number,title -q '.[0] | "#\(.number) \(.title)"' 2>/dev/null || echo '?')"
else
  row "PR" "${C_DIM}gh 미설치 — 건너뜀${C_0}"
fi

# ── 브랜치 보호: 필수 체크 드리프트 ───────────────────────────────
# 왜 여기 있나: required_status_checks는 **GitHub 설정에만** 있고 레포 안엔 없다.
#   → 새 검사를 만들고 필수 등록을 잊으면 아무 신호 없이 "권고"로 남는다.
#   실측(08-11): Design Integrity·gitleaks·tfsec·guard 4개가 정확히 그 상태였다.
#   그 중 Design Integrity 안에 훅 배선 검사가 살고 있었으니, 재발 방지 장치 자체가
#   빨개도 머지되는 상태였다. 08-12에 6개로 확대.
#   CI로는 못 잡는다 — 보호 규칙 조회에 admin 스코프가 필요한데 Actions의 GITHUB_TOKEN엔 없다.
#
#   두 방향을 다 본다 (비대칭이라서):
#     ① 필수인데 생산하는 job이 없다 → context 미보고 → PR이 Expected로 영구 대기 (fail-closed)
#     ② PR에서 도는데 필수가 아니다  → 빨개도 머지된다 (fail-open, 조용함) ← 이번에 당한 쪽
hdr "보호"
if command -v gh >/dev/null 2>&1; then
  CTX=$(gh api "repos/:owner/:repo/branches/main/protection" \
          --jq '.required_status_checks.contexts[]' 2>/dev/null)
  if [ -z "$CTX" ]; then
    row "필수 체크" "${C_DIM}조회 실패 (권한/오프라인) — 건너뜀${C_0}"
  else
    # **모든** PR에서 무조건 도는 워크플로의 job 이름만 모은다.
    #   `pull_request:`에 paths/branches 필터가 붙어 있으면 해당 없는 PR에선 아예 안 돈다
    #   → 그런 검사를 필수로 걸면 그 PR은 "Expected"로 **영구 대기**한다. 필수 후보가 아니다.
    #   (실측: ecr-push=paths:be/**, infra-deploy=paths:infra/aws-eks/** — 둘 다 제외돼야 정상)
    # job은 4칸 들여쓰기 `name:`, step은 `- name:`이라 정규식으로 갈린다.
    JOBS=$(for f in "$ROOT"/.github/workflows/*.yml; do
             [ -f "$f" ] || continue
             awk '
               /^on:/ { inon=1; next }
               inon && /^[a-zA-Z]/ { inon=0 }
               inon && /^  pull_request:[[:space:]]*$/ { inpr=1; bare=1; next }
               inpr && /^  [a-zA-Z]/ { inpr=0 }
               inpr && /^    (paths|paths-ignore|branches|branches-ignore):/ { bare=0 }
               END { exit (bare==1 ? 0 : 1) }
             ' "$f" || continue
             sed -n 's/^    name: *//p' "$f"
           done)
    DRIFT=0
    while IFS= read -r c; do
      [ -n "$c" ] || continue
      printf '%s\n' "$JOBS" | grep -qxF "$c" || { row "생산자 없음" "${C_R}${c}${C_0}"; DRIFT=1; }
    done <<< "$CTX"
    while IFS= read -r j; do
      [ -n "$j" ] || continue
      printf '%s\n' "$CTX" | grep -qxF "$j" || { row "권고 상태" "${C_Y}${j}${C_0}  ${C_DIM}빨개도 머지됨${C_0}"; DRIFT=1; }
    done <<< "$JOBS"
    NC=$(printf '%s\n' "$CTX" | grep -c . )
    if [ "$DRIFT" -eq 0 ]; then
      row "필수 체크" "${C_G}${NC}개 · PR 검사 전부 필수${C_0}"
    else
      row "필수 체크" "${NC}개 ${C_Y}· 드리프트 있음(위)${C_0}"
    fi
  fi
else
  row "보호" "${C_DIM}gh 미설치 — 건너뜀${C_0}"
fi

# ── 미해결 지적 원장 ──────────────────────────────────────────────
# review-ledger.md의 "미해결 (open)" 절에 있는 표 행(| L-…)만 센다.
hdr "원장"
LEDGER="$ROOT/.claude/review-ledger.md"
if [ -f "$LEDGER" ]; then
  N=$(awk '/^## 미해결/{f=1;next} /^## /{f=0} f && /^\| L-/{c++} END{print c+0}' "$LEDGER")
  row "미해결 지적" "${N}건  ${C_DIM}(.claude/review-ledger.md)${C_0}"
else
  row "미해결 지적" "${C_DIM}원장 없음${C_0}"
fi

# ── EKS 과금 상태 ─────────────────────────────────────────────────
# 🔴 가장 중요한 줄. 세션 마커가 있으면 AWS에 과금 리소스가 떠 있을 수 있다.
hdr "EKS"
MARKER="$ROOT/.claude/eks-session/active"
if [ -f "$MARKER" ]; then
  AT=$(grep '^applied_at_h=' "$MARKER" 2>/dev/null | cut -d= -f2-)

  # 🔴 **마커를 믿지 말고 AWS에 확인한다.**
  #   마커는 `tofu apply` 명령을 감지하는 PreToolUse 훅이 만드는데, 이 훅은
  #   **명령 문자열에 그 단어가 있기만 해도** 반응한다. 예를 들어 문서에
  #   "제거 절차: tofu apply -var ..." 를 써넣는 것만으로 마커가 생긴다(2026-07-31 실측).
  #
  #   그러면 아무것도 안 떠 있는데 대시보드가 "🔴 과금 중"이라고 말한다 —
  #   **거짓 경보는 경보를 죽인다.** 몇 번 겪으면 진짜 경고도 눈으로 넘기게 된다.
  #   (오늘 SOP 고아 검사에서 고친 것과 같은 병이다.)
  #
  #   → 마커는 "확인해볼 이유"일 뿐이고, **판정은 AWS 실물이 한다.**
  LIVE=""
  if command -v aws >/dev/null 2>&1; then
    AWSQ2=(aws --cli-connect-timeout 3 --cli-read-timeout 5 --region "${EKS_REGION:-ap-northeast-2}")
    C=$("${AWSQ2[@]}" eks list-clusters --query 'clusters' --output text 2>/dev/null)
    D=$("${AWSQ2[@]}" rds describe-db-instances \
      --query 'DBInstances[?starts_with(DBInstanceIdentifier, `devquest`)].DBInstanceIdentifier' \
      --output text 2>/dev/null)
    LIVE="${C}${D}"
  fi

  if [ -n "$LIVE" ]; then
    row "세션 마커" "${C_R}🔴 활성 — apply ${AT:-시각미상}. ${C_B}실제로 과금 중${C_0}"
    row "" "${C_DIM}끝났다면 destroy: docs/eks-session-sop.md §8${C_0}"
  else
    row "세션 마커" "${C_Y}⚠️ 유령 마커 — 마커는 있으나 EKS·RDS 실물이 없다${C_0}"
    row "" "${C_DIM}과금 아님. 훅 오탐이거나 destroy 후 정리가 안 된 것 — 리퍼가 다음 주기에 자가 청소한다.${C_0}"
    row "" "${C_DIM}즉시 지우려면: rm -f .claude/eks-session/active${C_0}"
  fi
else
  row "세션 마커" "${C_G}없음 — 클러스터 미가동${C_0}"
fi

# 영속 리소스: destroy로 사라지지 않는 것. 조회 실패는 조용히 넘어간다.
if command -v aws >/dev/null 2>&1; then
  REGION="${EKS_REGION:-ap-northeast-2}"
  VOLS=$(aws --cli-connect-timeout 3 --cli-read-timeout 5 --region "$REGION" \
    ec2 describe-volumes --filters Name=tag:Persistent,Values=true \
    --query 'Volumes[].[VolumeId,Size,AvailabilityZone]' --output text 2>/dev/null)
  AWS_RC=$?   # ⚠️ 대입 직후에 잡는다 — if 안에서 읽으면 앞 test의 결과가 잡힌다.
  if [ "$AWS_RC" -eq 0 ] && [ -n "$VOLS" ]; then
    GB=$(echo "$VOLS" | awk '{s+=$2} END{print s+0}')
    row "영속 EBS" "$(echo "$VOLS" | wc -l | tr -d ' ')개 · ${GB}GiB ≈ \$$(awk -v g="$GB" 'BEGIN{printf "%.2f", g*0.0912}')/월"
    row "" "${C_DIM}기대값 대조: infra/aws-eks/PERSISTENT-RESOURCES.md${C_0}"
  elif [ "$AWS_RC" -eq 0 ]; then
    row "영속 EBS" "0개"
  fi
fi

hdr "다음 할 일"
printf "  ${C_DIM}.claude/CONTEXT.md 의 \"다음 작업\" 절을 읽으세요 (파생 불가능한 상태는 거기 있습니다).${C_0}\n\n"
