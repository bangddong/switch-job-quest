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
# 실제 과금 리소스가 남아있는지 AWS로 확인.
#
# 🔴 EKS 클러스터만 보면 안 된다 (Stage 2에서 RDS가 추가되며 드러난 구멍):
#   destroy가 부분 실패해 "EKS는 지워졌는데 RDS는 남은" 상태가 되면,
#   클러스터만 보는 판정은 "이미 정리됨"으로 오판하고 **마커를 지워 감시를 끝낸다.**
#   그러면 RDS가 아무도 안 보는 채로 계속 과금된다($0.025/hr = 방치 1주일 $4.2).
#   → 생존 판정은 **과금되는 모든 리소스의 OR**여야 한다.
CLUSTERS=$(aws eks list-clusters --region "$REGION" --query 'clusters' --output text 2>/dev/null)
# 이름 prefix로 우리 리소스만 대상으로 한다 — 같은 계정의 무관한 DB를 건드리지 않기 위해.
RDS=$(aws rds describe-db-instances --region "$REGION" \
  --query 'DBInstances[?starts_with(DBInstanceIdentifier, `devquest`)].DBInstanceIdentifier' \
  --output text 2>/dev/null)

if [ -z "$CLUSTERS" ] && [ -z "$RDS" ]; then
  # 과금 리소스 전부 없음 → 마커만 청소.
  log "마커 있으나 과금 리소스 없음(EKS·RDS 모두) — 마커 자가 청소."
  rm -f "$MARKER" "$DIR/lastcheck" "$DIR/state.cache" 2>/dev/null
  exit 0
fi

# 한쪽만 남은 경우 = 이전 destroy가 부분 실패했다는 신호. 눈에 띄게 남긴다.
if [ -z "$CLUSTERS" ] && [ -n "$RDS" ]; then
  log "⚠️ 부분 잔존 감지 — EKS는 없는데 RDS 생존 [$RDS]. 이전 destroy가 중간에 실패했을 가능성."
fi

CLUSTER_DIR=$(grep '^cluster_dir=' "$MARKER" 2>/dev/null | cut -d= -f2-)
APPLIED_H=$(grep '^applied_at_h=' "$MARKER" 2>/dev/null | cut -d= -f2-)

log "🔴 DEAD MAN'S SWITCH 발동 — 하트비트 ${STALE}s stale(TTL ${TTL}s), 생존: EKS[${CLUSTERS:-없음}] RDS[${RDS:-없음}]."
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
  # 무엇이 살아남았는지 즉시 남긴다. destroy가 실패하면 다음 주기까지 30분이 비는데,
  # 그때 로그에 "실패"만 있으면 무엇이 과금 중인지 알 수 없다.
  log "   잔존 확인: EKS[$(aws eks list-clusters --region "$REGION" --query 'clusters' --output text 2>/dev/null || echo '조회실패')]"
  log "   잔존 확인: RDS[$(aws rds describe-db-instances --region "$REGION" --query 'DBInstances[].DBInstanceIdentifier' --output text 2>/dev/null || echo '조회실패')]"
  exit 1
fi
exit 0
