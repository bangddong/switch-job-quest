#!/usr/bin/env bash
# Karpenter 가 만든 AWS 리소스를 지운다. `tofu destroy` 가 부른다(karpenter-cleanup.tf).
# 사람이 직접 돌려도 된다:
#   CLUSTER_NAME=devquest-eks REGION=ap-northeast-2 NODE_ROLE=devquest-eks-node-role bash karpenter-cleanup.sh
#
# 왜 있는가: Karpenter 가 띄운 EC2 · 시작 템플릿 · 인스턴스 프로파일은 tofu state 에 없다.
#   클러스터만 지우면 EC2 는 주인 없이 남아 과금되고, 프로파일이 노드 역할을 물고 있어
#   역할 삭제가 DeleteConflict 로 실패한다.
#
# 🔴 **Karpenter 컨트롤러가 죽은 뒤에** 돌아야 한다. 살아 있으면 EC2 를 지우는 족족 다시 띄운다.
#    tofu 에서는 의존 순서로 보장한다(노드그룹 삭제 → 이 스크립트 → 노드 역할 삭제).
#
# 지우는 대상은 **Karpenter 태그 + 이 클러스터 태그가 둘 다 붙은 것**뿐이다. 같은 계정의 다른
# 인스턴스를 건드리지 않는다. Karpenter 를 쓰지 않은 세션에서는 아무것도 찾지 못하고 끝난다.
set -uo pipefail

: "${CLUSTER_NAME:?CLUSTER_NAME 필요}"
: "${REGION:?REGION 필요}"
: "${NODE_ROLE:?NODE_ROLE 필요}"

OWNED="Name=tag:kubernetes.io/cluster/${CLUSTER_NAME},Values=owned"
POOL="Name=tag-key,Values=karpenter.sh/nodepool"
ALIVE="Name=instance-state-name,Values=pending,running,stopping,stopped"

list_instances() {
  aws ec2 describe-instances --region "$REGION" --filters "$OWNED" "$POOL" "$ALIVE" \
    --query 'Reservations[].Instances[].InstanceId' --output text
}

# ── ① EC2 ────────────────────────────────────────────────────────────────
if ! IDS=$(list_instances); then
  echo "[karpenter-cleanup] ⛔ EC2 조회 실패 — 자격증명·네트워크 확인. 아무것도 지우지 않았다." >&2
  exit 1
fi

if [ -n "$IDS" ]; then
  echo "[karpenter-cleanup] EC2 종료: $IDS"
  # shellcheck disable=SC2086  # 인스턴스 ID 목록을 단어 분리로 넘긴다
  aws ec2 terminate-instances --region "$REGION" --instance-ids $IDS >/dev/null || exit 1
  # shellcheck disable=SC2086
  aws ec2 wait instance-terminated --region "$REGION" --instance-ids $IDS || exit 1
else
  echo "[karpenter-cleanup] Karpenter EC2 없음"
fi

# ── ② 시작 템플릿 ─────────────────────────────────────────────────────────
LTS=$(aws ec2 describe-launch-templates --region "$REGION" --filters "$OWNED" "$POOL" \
  --query 'LaunchTemplates[].LaunchTemplateId' --output text 2>/dev/null || true)
for lt in $LTS; do
  echo "[karpenter-cleanup] 시작 템플릿 삭제: $lt"
  aws ec2 delete-launch-template --region "$REGION" --launch-template-id "$lt" >/dev/null || true
done

# ── ③ 인스턴스 프로파일 ───────────────────────────────────────────────────
# 노드 역할에 붙은 프로파일 중 **Karpenter 태그가 있는 것만** 뗀다.
# (관리형 노드그룹의 프로파일은 EKS 가 만들고 EKS 가 지운다 — 건드리지 않는다.)
PROFILES=$(aws iam list-instance-profiles-for-role --role-name "$NODE_ROLE" \
  --query 'InstanceProfiles[].InstanceProfileName' --output text 2>/dev/null || true)
for p in $PROFILES; do
  IS_KARPENTER=$(aws iam list-instance-profile-tags --instance-profile-name "$p" \
    --query 'length(Tags[?Key==`karpenter.k8s.aws/ec2nodeclass`])' --output text 2>/dev/null || echo 0)
  [ "$IS_KARPENTER" = "1" ] || continue
  echo "[karpenter-cleanup] 인스턴스 프로파일 삭제: $p"
  aws iam remove-role-from-instance-profile --instance-profile-name "$p" --role-name "$NODE_ROLE" || true
  aws iam delete-instance-profile --instance-profile-name "$p" || true
done

# ── ④ 완료 확인 — "지우라고 했다"와 "없다"는 다르다 ──────────────────────────
LEFT=$(list_instances) || exit 1
if [ -n "$LEFT" ]; then
  echo "[karpenter-cleanup] ⛔ 종료 후에도 EC2 가 남았다: $LEFT" >&2
  exit 1
fi
echo "[karpenter-cleanup] ✅ 잔존 EC2 0"
