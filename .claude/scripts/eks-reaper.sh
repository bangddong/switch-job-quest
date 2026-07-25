#!/usr/bin/env bash
# EKS dead man's switch — launchd가 30분마다 실행.
# 규칙: EKS 세션 마커가 있고 + 하트비트가 TTL 이상 stale이면 → 실제 클러스터 확인 후 tofu destroy.
#   - 하트비트가 신선(사람 활동 중)하면 아무것도 안 한다.
#   - 마커는 있는데 클러스터가 이미 없으면 마커만 청소(자가 치유).
# launchd는 최소 환경으로 실행되므로 PATH/리전을 명시한다. AWS 자격증명은 ~/.aws 기본 프로파일 사용.
#
# 환경변수:
#   EKS_REAPER_TTL   하트비트 stale 임계(초). 기본 7200(2h).
#   EKS_REAPER_DRYRUN=1  destroy 대신 로그만 (테스트용).
#   EKS_REGION       기본 ap-northeast-2.

# launchd는 최소 PATH(/usr/bin:/bin)로 실행 → tofu/aws가 없다. homebrew 경로를 뒤에 붙여
# fallback으로 찾게 한다(append라 테스트에서 앞쪽에 mock을 주입할 수 있음).
export PATH="$PATH:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
REGION="${EKS_REGION:-ap-northeast-2}"
TTL="${EKS_REAPER_TTL:-7200}"

# 마커 위치: 이 스크립트는 <repo>/.claude/scripts/ 에 있으므로 repo root를 상대적으로 찾는다.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DIR="$ROOT/.claude/eks-session"
MARKER="$DIR/active"
LOG="$DIR/reaper.log"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S %Z')] $*" >> "$LOG" 2>/dev/null; }

# 마커 없음 → 감시할 세션 없음. 조용히 종료.
[ -f "$MARKER" ] || exit 0

# 하트비트 신선도 확인
HB="$DIR/heartbeat"
NOW=$(date +%s)
HB_MTIME=0
if [ -f "$HB" ]; then
  # macOS stat -f %m, GNU stat -c %Y — 둘 다 시도
  HB_MTIME=$(stat -f %m "$HB" 2>/dev/null || stat -c %Y "$HB" 2>/dev/null || echo 0)
fi
STALE=$(( NOW - HB_MTIME ))

if [ "$STALE" -lt "$TTL" ]; then
  # 사람이 아직 활동 중(하트비트 신선) → 건드리지 않는다.
  exit 0
fi

# 여기부터: 하트비트가 TTL 이상 stale = 사람이 사라졌다고 판단.
# 실제 클러스터가 있는지 AWS로 확인.
CLUSTERS=$(aws eks list-clusters --region "$REGION" --query 'clusters' --output text 2>/dev/null)

if [ -z "$CLUSTERS" ]; then
  # 이미 클러스터 없음 → 마커만 청소.
  log "마커 있으나 클러스터 없음 — 마커 자가 청소."
  rm -f "$MARKER" "$DIR/lastcheck" "$DIR/state.cache" 2>/dev/null
  exit 0
fi

CLUSTER_DIR=$(grep '^cluster_dir=' "$MARKER" 2>/dev/null | cut -d= -f2-)
APPLIED_H=$(grep '^applied_at_h=' "$MARKER" 2>/dev/null | cut -d= -f2-)

log "🔴 DEAD MAN'S SWITCH 발동 — 하트비트 ${STALE}s stale(TTL ${TTL}s), 클러스터 [$CLUSTERS] 생존."
log "   apply 시각: ${APPLIED_H:-미상} / destroy 실행: $CLUSTER_DIR"

if [ "${EKS_REAPER_DRYRUN:-0}" = "1" ]; then
  log "   [DRYRUN] 실제 destroy 생략 — 실행됐다면: (cd $CLUSTER_DIR && tofu destroy -auto-approve)"
  exit 0
fi

if [ ! -d "$CLUSTER_DIR" ]; then
  log "   ⛔ cluster_dir 없음($CLUSTER_DIR) — 수동 확인 필요. 마커 유지."
  exit 1
fi

cd "$CLUSTER_DIR" || { log "   ⛔ cd 실패 — 마커 유지."; exit 1; }
tofu init -input=false >> "$LOG" 2>&1
if tofu destroy -auto-approve -no-color >> "$LOG" 2>&1; then
  log "   ✅ tofu destroy 완료 — 과금 종료. 마커 청소."
  rm -f "$MARKER" "$DIR/lastcheck" "$DIR/state.cache" 2>/dev/null
else
  log "   ⛔ tofu destroy 실패 — 마커 유지, 다음 주기 재시도. 수동 확인 권장."
  exit 1
fi
exit 0
