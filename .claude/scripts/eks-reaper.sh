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

# ── 고아 EBS 경고 (감지 전용, 삭제하지 않는다) ──────────────────────
#
# 왜 "경고만" 인가 — 리퍼가 할 수 있는 일은 `tofu destroy` 하나뿐이다.
# K8s가 CSI로 만든 볼륨은 **tofu state 밖**이라 destroy로 회수되지 않는다.
# 즉 리퍼는 이 고아를 **볼 수는 있어도 지울 수는 없다.**
#
# 🔴 그래서 이것을 "생존 판정"에 넣으면 안 된다.
#    넣으면: 고아 볼륨 존재 → 생존으로 판정 → destroy 시도 → 볼륨은 그대로 →
#    다음 주기에 또 같은 판정 → **영원히 끝나지 않는 destroy 루프**가 된다.
#    감지와 조치를 분리하고, 조치는 다음 세션의 사람에게 넘긴다.
#
# 영속 볼륨(Persistent=true)은 남아 있는 게 **정상**이므로 제외한다.
# 값까지 비교하는 이유는 PERSISTENT-RESOURCES.md의 반증 테스트 주석 참조.
warn_orphan_volumes() {
  local orphans
  orphans=$(aws ec2 describe-volumes --region "$REGION" \
    --filters Name=status,Values=available \
    --query "Volumes[?!(Tags[?Key=='Persistent' && Value=='true'])].[VolumeId,Size]" \
    --output text 2>/dev/null) || return 0
  [ -n "$orphans" ] || return 0
  log "⚠️ 고아 EBS 감지 — tofu destroy로는 회수 불가(K8s가 만든 볼륨은 state 밖). 수동 삭제 필요:"
  echo "$orphans" | while IFS=$'\t' read -r vid size; do
    log "     $vid  ${size}GiB   →  aws ec2 delete-volume --region $REGION --volume-id $vid"
  done
}

# ── Stage 4: ALB 회수 ───────────────────────────────────────────────
#
# 🔴 **왜 필요한가 — 리퍼가 도는 상황이 곧 사람이 없는 상황이다.**
# ALB 는 LBC 가 만들므로 **tofu state 밖**이고 `tofu destroy` 가 모른다. SOP §8 은
# `kubectl delete ingress` 를 사람에게 시키는데, 리퍼가 발동한다는 건 정확히
# **그 사람이 자리를 비웠다**는 뜻이다. 방어가 없으면 월 $16.43 + IP $7.30 이 조용히 나간다.
#
# 🔴 **destroy 보다 먼저 해야 한다.** LBC 는 ALB 용 SG 를 만들고 그 SG 를 참조하는
# ingress rule 을 클러스터/노드 SG 에 추가한다. Ingress 를 남긴 채 destroy 하면
# `DependencyViolation` 으로 SG 삭제가 실패하고 → 리퍼는 "실패, 다음 주기 재시도"를
# **영원히 반복**한다. 위 warn_orphan_volumes 주석이 EBS 로 경계한 그 루프의 ALB 판이다.
#
# ⚠️ **타임아웃이 필수다.** ALB Ingress 에는 finalizer 가 붙는다. 컨트롤러 파드가 먼저
# 죽었거나 evict 됐으면 delete 가 **영구 hang** 하고, 그러면 리퍼 자체가 멈춰
# destroy 에 도달하지 못한다 — 안전장치를 고치려다 안전장치를 벽돌로 만드는 것이다.
# 그래서 실패해도 **막지 않고 넘어간다**(성패 판정은 뒤의 warn_orphan_albs 가 한다).
cleanup_k8s_loadbalancers() {
  command -v kubectl >/dev/null 2>&1 || { log "   ℹ️ kubectl 없음 — Ingress 정리 생략(ALB 고아 가능)"; return 0; }

  # 리퍼는 사람 셸과 다른 환경에서 돈다(launchd). kubeconfig 가 없거나 옛 클러스터를
  # 가리킬 수 있으므로 갱신한다. 실패하면 클러스터가 이미 없다는 뜻이라 정리할 것도 없다.
  aws eks update-kubeconfig --name "$1" --region "$REGION" >/dev/null 2>&1 || {
    log "   ℹ️ kubeconfig 갱신 실패 — 클러스터 접근 불가로 보고 Ingress 정리 생략"; return 0; }

  local n
  n=$(kubectl get ingress -A --no-headers 2>/dev/null | grep -c . || true)
  [ "${n:-0}" -gt 0 ] || { log "   ℹ️ Ingress 0건 — 정리할 ALB 없음"; return 0; }

  log "   🧹 Ingress ${n}건 삭제 시도(ALB 회수) — 타임아웃 120s"
  if kubectl delete ingress --all -A --timeout=120s >>"$LOG" 2>&1; then
    log "   ✅ Ingress 삭제 완료"
  else
    log "   ⚠️ Ingress 삭제 실패/타임아웃 — finalizer 잔존 의심. destroy 는 계속한다"
  fi
}

# destroy 후 ALB 가 남았는지 본다. warn_orphan_volumes 와 같은 성격 — **감지 전용**이다.
# 리퍼는 ALB 를 직접 지울 수 없다(그건 LBC 의 일이고 클러스터는 이미 없다).
warn_orphan_albs() {
  local albs
  albs=$(aws elbv2 describe-load-balancers --region "$REGION" \
    --query 'LoadBalancers[].[LoadBalancerName,LoadBalancerArn]' --output text 2>/dev/null) || return 0
  [ -n "$albs" ] || return 0
  log "⚠️ 고아 ALB 감지 — 클러스터가 사라져 LBC 가 회수할 수 없다. **수동 삭제 필요**(월 ~$16.43):"
  echo "$albs" | while IFS=$'\t' read -r name arn; do
    [ -n "$name" ] || continue
    log "     $name  →  aws elbv2 delete-load-balancer --region $REGION --load-balancer-arn $arn"
  done
}

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
  # ⚠️ 마커를 지우면 이 세션에 대한 감시가 끝난다. 그 전에 고아 볼륨을 반드시 남긴다 —
  #    여기서 안 적으면 다음 세션까지 아무 기록도 없이 과금이 계속된다.
  warn_orphan_volumes
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

# 🔴 destroy 보다 **먼저** — ALB 를 회수하고 SG DependencyViolation 을 피한다(위 함수 주석 참조).
# 클러스터 이름은 생존 판정에서 이미 조회한 $CLUSTERS 의 첫 항목을 쓴다(보통 1개).
cleanup_k8s_loadbalancers "$(echo "$CLUSTERS" | awk '{print $1}')"

tofu init -input=false >> "$LOG" 2>&1
if tofu destroy -auto-approve -no-color >> "$LOG" 2>&1; then
  # destroy 성공 = tofu가 소유한 것은 전부 회수됐다는 뜻이다.
  # 그래도 남아 있는 available 볼륨이 있다면 그건 정의상 **state 밖에서 만들어진 것**이다.
  warn_orphan_volumes
  # ALB 도 같은 성격의 state 밖 리소스다. 위 cleanup 이 성공했으면 0건이어야 한다 —
  # 여기서 뭔가 잡히면 cleanup 이 실패했다는 뜻이고, 그 사실을 로그에 남겨야 다음 사람이 안다.
  warn_orphan_albs
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
