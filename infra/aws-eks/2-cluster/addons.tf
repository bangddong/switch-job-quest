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

  # 🆘 **켰다 (Stage 3b).** 파드 상한 11에 실제로 부딪혔으므로 목적을 달성했다.
  #
  # Stage 3a 실측: 시스템 4 + ESO 3 + coredns 2 + core-api 1 + postgres 1 = **11/11, 여유 0.**
  # `kubectl rollout restart`가 실패했다 —
  #   0/1 nodes are available: 1 Insufficient memory, 1 Too many pods
  # 롤링 업데이트는 **새 파드를 먼저 띄우고** 구 파드를 내리므로 빈 슬롯 1칸이 필요한데 없었다.
  # (그때는 `scale 0→1`로 우회했다. 삭제 후 생성이라 슬롯이 안 필요하다.)
  #
  # 3b는 StatefulSet을 지웠다 다시 만드는 작업이라 이 벽에 정면으로 부딪힌다.
  #
  # 왜 coredns를 줄이는 것이 공짜인가: **노드가 1대라 replica 2개는 HA가 아니다.**
  # 둘 다 같은 노드에 뜨므로 노드가 죽으면 함께 죽는다. 가용성은 0도 2도 아니고 그냥 같다.
  # 즉 두 번째 replica는 슬롯만 먹고 아무것도 보장하지 않았다.
  #
  # ⚠️ 노드를 2대 이상으로 늘리면 이 판단이 뒤집힌다 — 그때는 2로 되돌릴 것.
  #    (파드 상한도 노드당 11이라 함께 늘어난다.)
  configuration_values = jsonencode({ replicaCount = 1 })
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
