# ⑮ Karpenter 가 만든 것까지 `tofu destroy` 하나로 지운다 (Stage 5b, 계획서 D9)
#
# 🔴 문제: Karpenter 가 띄운 EC2 · 시작 템플릿 · 인스턴스 프로파일은 **tofu state 에 없다.**
#   tofu 는 자기가 만든 것만 지운다. 클러스터를 destroy 하면
#     ① EC2 가 주인 없이 남아 계속 과금된다 (t4g.small 한 대 ≈ 월 $20)
#     ② 프로파일이 노드 역할을 물고 있어 `aws_iam_role.node` 삭제가 DeleteConflict 로 실패한다
#   자동 종료 장치(리퍼)는 `tofu destroy` 만 친다. 사람이 사라진 상황에서
#   `kubectl delete nodepool` 을 대신 쳐 줄 주체가 없다.
#
# 해법: 삭제 시점에만 도는 실행기를 **의존 순서의 한가운데**에 끼운다.
#
#   만들 때   노드 역할 → (이 리소스: 아무 일도 안 한다) → 노드그룹
#   지울 때   노드그룹 → **이 리소스: 정리 스크립트 실행** → 노드 역할
#
#   tofu 는 의존의 역순으로 지운다. 그래서
#     · 이 리소스가 노드 역할을 참조하면      → 역할보다 **먼저** 지워진다
#     · 노드그룹이 이 리소스에 의존하면(nodes.tf) → 노드그룹보다 **나중에** 지워진다
#
# 🔑 왜 노드그룹 **뒤**여야 하는가: Karpenter 컨트롤러는 관리형 노드그룹 위에서 돈다.
#   컨트롤러가 살아 있을 때 EC2 를 지우면 Pending 파드를 보고 **다시 띄운다.**
#   노드그룹이 사라지면 컨트롤러가 죽고, 그 뒤에 지워야 끝난다.
#
# 🔑 **클러스터보다도 먼저** 돌아야 한다. EKS 는 클러스터를 지울 때 클러스터 보안그룹을 함께 지우는데,
#   Karpenter EC2 의 네트워크 인터페이스가 그 보안그룹을 쓰고 있으면 삭제가 걸린다.
#   그래서 지울 때의 전체 순서는   노드그룹 → 정리 → { 노드 역할 · 클러스터 }   다.
#
# ⚠️ 삭제 시점 실행기는 `self` 만 참조할 수 있다(tofu 제약). 그래서 필요한 값을 `input` 에 담아 둔다.
# ⚠️ 실행 환경에 aws CLI 가 있어야 한다. 이 레이어는 로컬에서만 apply·destroy 한다(CI 는 validate 까지).
# ⚠️ Karpenter 를 설치하지 않은 세션에서도 돈다 — 스크립트가 아무것도 못 찾고 몇 초 만에 끝난다.
resource "terraform_data" "karpenter_cleanup" {
  input = {
    # var.cluster_name 이 아니라 **리소스를 참조한다** — 그래야 클러스터보다 먼저 지워진다(위 🔑).
    cluster_name = aws_eks_cluster.main.name
    region       = var.region
    node_role    = aws_iam_role.node.name
  }

  provisioner "local-exec" {
    when    = destroy
    command = "bash \"${path.module}/scripts/karpenter-cleanup.sh\""

    environment = {
      CLUSTER_NAME = self.input.cluster_name
      REGION       = self.input.region
      NODE_ROLE    = self.input.node_role
    }
  }
}
