# ── ⑤ 관리형 노드그룹 (t4g.small, 기본 ON_DEMAND) ────────────────
# capacity_type 기본값은 ON_DEMAND. 신규 계정 Spot vCPU 쿼터=0 → SPOT이면 apply 실패.
# 스팟 학습 시 쿼터 증액 후 var.node_capacity_type=SPOT 주입. (근거: variables.tf 주석)
resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-ng"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = data.terraform_remote_state.network.outputs.public_subnet_ids

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

  depends_on = [
    aws_iam_role_policy_attachment.node_worker,
    aws_iam_role_policy_attachment.node_cni,
    aws_iam_role_policy_attachment.node_ecr,
  ]
}
