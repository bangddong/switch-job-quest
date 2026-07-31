# ── ⑤ 관리형 노드그룹 (t4g.small, 기본 ON_DEMAND) ────────────────
# capacity_type 기본값은 ON_DEMAND. 신규 계정 Spot vCPU 쿼터=0 → SPOT이면 apply 실패.
# 스팟 학습 시 쿼터 증액 후 var.node_capacity_type=SPOT 주입. (근거: variables.tf 주석)
resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-ng"
  node_role_arn   = aws_iam_role.node.arn

  # ── 🔴 노드를 영속 EBS와 같은 AZ에 고정한다 (Stage 3b) ────────────────
  #
  # 종전: `...outputs.public_subnet_ids` = [2a서브넷, 2c서브넷] 둘 다 전달.
  #       노드는 1대(desired_size=1)인데 후보지가 둘 → **어디에 뜰지 AWS ASG가 정한다.**
  #       코드에 pin이 없어 매 세션 동전 던지기였다.
  #
  # Stage 3a는 이게 안 터졌다. 운이 아니라 `volumeBindingMode: WaitForFirstConsumer` 덕이다 —
  # 파드가 스케줄된 **뒤에** 그 노드의 AZ에 볼륨을 만드니 구조적으로 어긋날 수 없었다.
  #
  # 🔴 3b는 그 방어막이 없다. 볼륨이 **먼저** 존재하므로, 노드가 반대 AZ에 뜨면
  #    attach 자체가 불가능하다 → 파드 영구 Pending(실패 6종 ①). 확률 50%,
  #    즉 "운 나쁜 날에만 터지는" 버그가 된다 — 가장 잡기 어려운 종류다.
  #
  # → 후보지를 하나로 줄여 원천 제거한다. AZ의 단일 출처는 0-bootstrap이다.
  #
  # 대가: 그 AZ에 용량이 없으면(InsufficientInstanceCapacity) apply가 실패하고 폴백이 없다.
  #       서울 리전 t4g.small 온디맨드에서는 사실상 발생하지 않는다.
  # ℹ️ 컨트롤플레인(cluster.tf)은 계속 2 AZ를 받는다 — EKS API가 강제하는 요구사항이라
  #    좁힐 수 없고, 좁힐 이유도 없다(ENI만 놓일 뿐 워크로드가 뜨지 않는다).
  subnet_ids = [
    data.terraform_remote_state.network.outputs.public_subnet_ids_by_az[
      data.terraform_remote_state.bootstrap.outputs.persistent_az
    ]
  ]

  ami_type       = "AL2023_ARM_64_STANDARD"
  instance_types = [var.node_instance_type]
  capacity_type  = var.node_capacity_type

  scaling_config {
    desired_size = var.node_desired_size
    min_size     = var.node_min_size
    max_size     = var.node_max_size
  }

  update_config {
    max_unavailable = 1
  }

  # 🔴 persistent_az가 1-network의 AZ 목록 안에 있는지 먼저 확인한다.
  #
  # 없으면 위 subnet_ids의 맵 조회가 이렇게 실패한다:
  #   Invalid index: the given key does not identify an element in this collection value
  # — **어느 변수가 문제인지 한 마디도 안 알려준다.** 레이어 3개를 오가며 찾아야 한다.
  # 여기서 이름을 대고 막으면 그 시간이 0이 된다.
  #
  # (QA F-2: 이 precondition이 `1-network`의 azs 출력을 실제로 소비한다.
  #  안 쓰면 소비처 없는 죽은 설정이 된다 — #326에서 두 번 걷어낸 유형.)
  lifecycle {
    precondition {
      condition = contains(
        data.terraform_remote_state.network.outputs.azs,
        data.terraform_remote_state.bootstrap.outputs.persistent_az,
      )
      error_message = format(
        "persistent_az(%s)가 1-network의 AZ 목록%v 밖입니다. 0-bootstrap의 var.persistent_az를 목록 안의 값으로 바꾸거나, 1-network의 var.azs에 해당 AZ를 추가하세요.",
        data.terraform_remote_state.bootstrap.outputs.persistent_az,
        data.terraform_remote_state.network.outputs.azs,
      )
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.node_worker,
    aws_iam_role_policy_attachment.node_cni,
    aws_iam_role_policy_attachment.node_ecr,
  ]
}
