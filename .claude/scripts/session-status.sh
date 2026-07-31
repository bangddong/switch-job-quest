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
  row "세션 마커" "${C_R}🔴 활성 — apply ${AT:-시각미상}. 과금 중일 수 있음${C_0}"
  row "" "${C_DIM}끝났다면 destroy 후 마커 확인: docs/eks-session-sop.md §8${C_0}"
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
