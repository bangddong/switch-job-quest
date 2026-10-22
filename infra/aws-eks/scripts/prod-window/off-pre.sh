#!/usr/bin/env bash
# off 의 destroy **앞** 단계 — 📌D-016 8-B. 끝나면 리전에 로드밸런서가 0 이어야 한다. 아니면 rc≠0 → 워크플로가 destroy 하지 않는다.
#
# 순서가 전부다 (SOP §8 과 같은 이유 — 이 스크립트가 그 순서의 CI 판이다):
#   ① LB 기록 — 이름·생성 시각·LCU 합계를 Step Summary 에(B8-17). ALB 는 매일 지워지므로 지금 안 적으면 LCU 실측이 사라진다.
#      CloudWatch 지표는 LB 가 지워져도 남지만, **어느 LB 였는지**를 아는 사람이 없어진다.
#   ② ArgoCD Application 먼저(B4-2) — selfHeal 이 Ingress 삭제를 1~2초 만에 되살린다. Application 은 finalizer 가 없어
#      지우면 리소스는 남고 관리만 끊긴다.
#   ③ Ingress 삭제(타임아웃) — LBC 가 ALB 를 지운 뒤에야 오브젝트가 사라진다.
#   ④ **AWS 에 직접 묻는다** — "kubectl 이 지웠다" 가 아니라 LB 수 0 이 합격이다. 남으면 LBC 를 멈추고(되살리지 않게)
#      aws CLI 로 지운다. 그래도 남으면 실패 — destroy 하면 LBC SG 참조로 DependencyViolation + ALB 고아(SOP §8 ②).
#
# 🔴 리퍼 로직을 그대로 가져오지 않은 곳 (B8-16·B4-3):
#   · kubeconfig·kubectl 실패를 "Ingress 없음" 으로 읽지 않는다 — K8s 단계는 건너뛰되 ④의 AWS 판정이 최종이다.
#   · `kubectl get … 2>/dev/null | grep -c .` 류(Forbidden = 0건)를 쓰지 않는다.
set -uo pipefail
. "$(dirname "$0")/common.sh"

C=$(win_cluster_exists) || exit 2

# ── ① LB 기록 ─────────────────────────────────────────────────────────────
LBS=$(aws elbv2 describe-load-balancers --region "$WIN_REGION" \
        --query 'LoadBalancers[].[LoadBalancerArn,CreatedTime]' --output text) || { wlog "LB 조회 실패"; exit 2; }
if [ -n "$LBS" ]; then
  wsummary "### 로드밸런서 기록 (B8-17 — 10번 LCU 실측 재료)"
  wsummary '```'
  END=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  while read -r ARN CREATED; do
    [ -n "$ARN" ] || continue
    DIM="${ARN#*:loadbalancer/}"           # app/<name>/<id> — CloudWatch 차원 값. 계정 ID 없음
    LCU=$(aws cloudwatch get-metric-statistics --region "$WIN_REGION" --namespace AWS/ApplicationELB \
            --metric-name ConsumedLCUs --dimensions "Name=LoadBalancer,Value=$DIM" \
            --start-time "$CREATED" --end-time "$END" --period 3600 --statistics Sum \
            --query 'sum(Datapoints[].Sum)' --output text 2>/dev/null) || LCU="조회실패"
    wsummary "$DIM  created=$CREATED  ConsumedLCUs(합)=$LCU  (LCU-시간 ≈ \$0.008/개)"
    wlog "LB $DIM created=$CREATED LCU=$LCU"
  done <<<"$LBS"
  wsummary '```'
fi

# ── ②③ K8s 단계 (클러스터가 있을 때만) ─────────────────────────────────────
K8S_OK=0
if [ "$C" = yes ]; then
  if aws eks update-kubeconfig --region "$WIN_REGION" --name "$WIN_CLUSTER" >/dev/null \
     && kubectl get --raw /readyz >/dev/null; then
    K8S_OK=1
  else
    wlog "⚠️ kubectl 연결 실패 — K8s 단계를 건너뛰고 AWS 로 LB 를 직접 정리한다(④)"
  fi
fi
if [ "$K8S_OK" = 1 ]; then
  if kubectl get crd applications.argoproj.io >/dev/null 2>&1; then
    kubectl -n argocd delete application devquest-apps --ignore-not-found --timeout=60s || wlog "Application 삭제 실패"
    N=$(kubectl -n argocd get application -o name) || N="조회실패"
    [ -z "$N" ] || wlog "⚠️ Application 이 남았다: $N — selfHeal 이 Ingress 를 되살릴 수 있다"
  fi
  kubectl delete ingress --all -A --timeout=180s || wlog "Ingress 삭제가 타임아웃·실패 — ④ 에서 AWS 로 확인한다"
fi

# ── ④ AWS 판정 ────────────────────────────────────────────────────────────
WAIT="${WIN_LB_WAIT:-180}"; POLL="${WIN_POLL:-10}"   # 테스트가 줄인다
wait_lb_zero() {
  local n
  for _ in $(seq 1 $(( WAIT / POLL ))); do
    n=$(win_lb_count) || return 2
    [ "$n" = 0 ] && return 0
    sleep "$POLL"
  done
  return 1
}
wait_lb_zero; rc=$?
[ $rc = 2 ] && { wlog "LB 조회 실패"; exit 2; }
if [ $rc = 1 ]; then
  wlog "LB 가 남았다 — LBC 를 멈추고 aws CLI 로 지운다"
  [ "$K8S_OK" = 1 ] && { kubectl -n kube-system scale deploy aws-load-balancer-controller --replicas=0 || true; }
  for ARN in $(aws elbv2 describe-load-balancers --region "$WIN_REGION" --query 'LoadBalancers[].LoadBalancerArn' --output text); do
    aws elbv2 delete-load-balancer --region "$WIN_REGION" --load-balancer-arn "$ARN" || wlog "LB 삭제 실패: ${ARN#*:loadbalancer/}"
  done
  wait_lb_zero || { wlog "🔴 LB 가 여전히 남아 있다 — destroy 하지 않는다(SOP §8 ②). 다음 watchdog 이 다시 시도한다"; exit 1; }
fi
# LBC 가 만든 타깃 그룹 — LB 가 지워져도 남는다($0 이지만 누적). 실패해도 off 를 막지 않는다.
for TG in $(aws elbv2 describe-target-groups --region "$WIN_REGION" \
              --query "TargetGroups[?starts_with(TargetGroupName, 'k8s-')].TargetGroupArn" --output text 2>/dev/null); do
  aws elbv2 delete-target-group --region "$WIN_REGION" --target-group-arn "$TG" || wlog "TG 삭제 실패(무시)"
done
wlog "✅ 로드밸런서 0 — destroy 로 넘어간다"
