#!/usr/bin/env bash
# on 잡의 예산 게이트 — 📌D-016 Q-3 · Blindspot B8-22. 사용: budget-gate.sh <창 시간>
#
# 🔑 왜: Q-3 으로 **켜는 시각이 비용을 정한다**(고정 창 길이 없음). 사람이 일찍 켜거나 자주 켜면 예산이 샌다.
#    그래서 on 잡이 스스로 거부한다 — 남은 크레딧이 *"만료일까지의 상시분 + 안전 예비 + 이번 창"* 을 못 덮으면 켜지 않는다.
#
#    가용 = 남은 크레딧 − 예비 $30 − 상시분/일 × 만료까지 남은 일수
#    통과 = 가용 ≥ 창 시간 × 시간당 단가
#
# 재료: `aws freetier get-account-plan-state` — **무료**(Cost Explorer 는 요청당 $0.01 이라 쓰지 않는다).
#   ⚠️ 이 값은 실시간이 아니다(🟡 하루 단위 갱신으로 보인다 — 미실측). 그래서 단가에 LCU 최악을 넣어 보수적으로 잡는다.
# 단가 근거 (`docs/eks-cost-model.md`):
#   창 = t4g.small ×3 + 컨트롤플레인 + ALB ≈ $0.25/h(N-12 — 실제 ≈ $0.218 + ALB) + LCU 최악 $0.096 → **$0.35/h**
#   상시 = EC2 DB ≈ $38.5/91일 ≈ $0.42/일 + 영속 리소스 $1.14/월 ≈ $0.04/일 → **$0.5/일**(올림)
# 실패 방향: 조회 실패·해석 실패 = **거부**(rc 2). 판정 못 하면 켜지 않는다.
set -euo pipefail
HOURS="${1:?창 시간(정수) 필요}"
RESERVE="${WIN_RESERVE_USD:-30}"
FIXED_DAY="${WIN_FIXED_PER_DAY_USD:-0.5}"
RATE_H="${WIN_RATE_PER_HOUR_USD:-0.35}"
NOW="${WIN_NOW:-$(date +%s)}"
log() { echo "[budget-gate] $*" >&2; }

case "$HOURS" in ''|*[!0-9]*) log "창 시간이 정수가 아니다: $HOURS"; exit 2 ;; esac

STATE=$(aws freetier get-account-plan-state --region us-east-1 \
          --query '[accountPlanType, accountPlanRemainingCredits.amount, accountPlanExpirationDate]' --output text) \
  || { log "freetier 조회 실패 — 판정 불가, 켜지 않는다"; exit 2; }
read -r PLAN REMAIN EXPIRES <<<"$STATE"
[[ "$REMAIN" =~ ^[0-9]+(\.[0-9]+)?$ ]] || { log "남은 크레딧 해석 실패($REMAIN) — 켜지 않는다"; exit 2; }
# GNU date(러너) 먼저, 안 되면 BSD(macOS 로컬 테스트)
EXP_EPOCH=$(date -d "$EXPIRES" +%s 2>/dev/null \
  || date -j -f '%Y-%m-%dT%H:%M:%S' "${EXPIRES%%.*}" +%s 2>/dev/null) \
  || { log "만료일 해석 실패($EXPIRES) — 켜지 않는다"; exit 2; }
[ "$PLAN" = "FREE" ] || log "⚠️ 플랜이 FREE 가 아니다($PLAN) — 크레딧 소진이 계정 폐쇄가 아니게 됐을 수 있다. 판정은 그대로 한다"

DAYS=$(( (EXP_EPOCH - NOW + 86399) / 86400 ))
[ "$DAYS" -ge 0 ] || DAYS=0
read -r AVAIL NEED <<<"$(awk -v r="$REMAIN" -v res="$RESERVE" -v f="$FIXED_DAY" -v d="$DAYS" -v h="$HOURS" -v rate="$RATE_H" \
  'BEGIN { printf "%.2f %.2f\n", r - res - f * d, h * rate }')"
MSG="남은 크레딧 \$$REMAIN · 만료까지 ${DAYS}일 · 가용 \$$AVAIL (예비 \$$RESERVE · 상시 \$$FIXED_DAY/일 제외) · 이번 창 ${HOURS}h ≈ \$$NEED"
if awk -v a="$AVAIL" -v n="$NEED" 'BEGIN { exit !(a >= n) }'; then
  log "✅ 통과 — $MSG"
  echo "$MSG"
else
  log "🔴 거부 — $MSG"
  echo "$MSG"
  exit 1
fi
