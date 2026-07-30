# ── ⑥ 관리형 애드온 (노드 Ready 필수 3종) ─────────────────────
resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "vpc-cni"
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "kube-proxy"
}

resource "aws_eks_addon" "coredns" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "coredns"

  # coredns 파드가 스케줄되려면 노드가 있어야 함
  depends_on = [aws_eks_node_group.main]

  # 🆘 파드 상한(11)에 막히면 여기를 켠다 — coredns를 1개로 줄여 슬롯 1칸을 번다.
  #    노드가 1대뿐이라 replica 2개는 HA가 아니다(같은 노드에 둘 다 뜬다 = 함께 죽는다).
  #    지금 켜지 않는 이유: 먼저 한도에 실제로 부딪혀 보는 것이 학습이기 때문.
  #    Stage 3a 예상 사용량은 정확히 11/11이라 여유가 0이다.
  # configuration_values = jsonencode({ replicaCount = 1 })
}

# ── ⑫ EBS CSI 드라이버 (Stage 3a) ─────────────────────────────
#
# 이게 없으면 PVC를 만들어도 **영원히 Pending**이다. K8s 자체는 EBS가 뭔지 모른다 —
# "스토리지를 만들어 달라"는 요청을 클라우드 API 호출로 번역해 주는 게 CSI 드라이버다.
# (CSI = Container Storage Interface. K8s가 스토리지 벤더별 코드를 코어에서 걷어내고
#  표준 인터페이스로 뺀 결과물. 그래서 EBS·EFS·GCP PD가 전부 같은 방식으로 붙는다.)
#
# 파드 2개가 뜬다:
#   ebs-csi-controller (Deployment) — AWS API를 호출해 볼륨을 만들고 지운다. IRSA 필요.
#   ebs-csi-node       (DaemonSet)  — 노드에 볼륨을 attach/mount 한다. 노드마다 1개.
resource "aws_eks_addon" "ebs_csi" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "aws-ebs-csi-driver"

  # 🔴 이 한 줄이 IRSA를 애드온에 연결한다. 빼면 컨트롤러가 AWS 호출에서
  #    AccessDenied를 내고 PVC가 Pending에서 안 벗어난다(= "인가" 실패 모드, Stage 2 학습).
  service_account_role_arn = aws_iam_role.ebs_csi.arn

  # 스키마 실측(aws eks describe-addon-configuration, 07-30):
  #   controller.replicaCount     default 2, minimum 1
  #   defaultStorageClass.enabled default false
  #
  # replicaCount=1로 낮추는 이유는 **비용이 아니라 파드 상한**이다.
  # t4g.small의 max-pods는 11(= ENI 3 × (IP 4 - 1) + 2, 실측). 현재 8을 쓰고 있어
  # 기본값 2로 두면 12가 되어 파드 하나가 Pending에 갇힌다.
  # 노드 1대짜리 학습 클러스터에서 컨트롤러 2개는 어차피 HA가 아니다(같이 죽는다).
  #
  # defaultStorageClass를 안 켜는 이유: StorageClass를 직접 쓰는 게 이번 Stage의 목표다.
  # 자동 생성된 것을 쓰면 reclaimPolicy·volumeBindingMode를 왜 그렇게 두는지 안 배운다.
  configuration_values = jsonencode({
    controller = {
      replicaCount = 1
    }
  })

  # 애드온 파드가 스케줄되려면 노드가 있어야 한다(coredns와 같은 이유).
  depends_on = [aws_eks_node_group.main]
}
