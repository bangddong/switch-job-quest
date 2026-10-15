# ── ② EKS 컨트롤플레인 ─────────────────────────────────────────
# tfsec 근거 ignore:
#   public 엔드포인트 개방은 단독 데스크톱 학습 놀이터의 의도된 선택
#   (인증은 IAM + Access Entry로 게이트). prod면 CIDR 제한/private.
#   secret KMS 암호화는 KMS 비용 회피로 생략.
#   control-plane 로깅: ~~CloudWatch 비용 회피로 생략~~ → 🔁 2026-10-15 Stage 5f 에서 **변수 토글로 전환**
#     (`var.control_plane_log_types`, 기본 빈 목록 = 끔). 상시 켜지 않는 이유는 그대로 비용이고, 필요한 세션에서만 켠다.
#     기본이 끔이라 tfsec 경고(aws-eks-enable-control-plane-logging)는 계속 맞다 — ignore 를 유지한다.
#tfsec:ignore:aws-eks-no-public-cluster-access
#tfsec:ignore:aws-eks-no-public-cluster-access-to-cidr
#tfsec:ignore:aws-eks-enable-control-plane-logging
#tfsec:ignore:aws-eks-encrypt-secrets
resource "aws_eks_cluster" "main" {
  name     = var.cluster_name
  version  = var.kubernetes_version
  role_arn = aws_iam_role.cluster.arn

  vpc_config {
    subnet_ids              = data.terraform_remote_state.network.outputs.public_subnet_ids
    endpoint_public_access  = true
    endpoint_private_access = false
    public_access_cidrs     = ["0.0.0.0/0"]
  }

  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = false
  }

  # Stage 5f — 컨트롤플레인 로그. 받는 그룹은 0-bootstrap 소유(영속 · 보존 30일).
  enabled_cluster_log_types = var.control_plane_log_types

  lifecycle {
    # 🔴 로그를 켰는데 0-bootstrap 그룹 이름과 클러스터 이름이 어긋나면 EKS 는 `/aws/eks/<이 이름>/cluster` 를
    #    **스스로 만든다 — 보존 무기한 · state 밖.** 그 고아를 plan 단계에서 막는다.
    #    try(): 0-bootstrap 을 아직 apply 하지 않아 output 이 없을 때도 "다르다"로 판정해 시끄럽게 멈춘다.
    precondition {
      condition = length(var.control_plane_log_types) == 0 || try(
        data.terraform_remote_state.bootstrap.outputs.eks_control_plane_log_group, ""
      ) == "/aws/eks/${var.cluster_name}/cluster"
      error_message = "컨트롤플레인 로그를 켜려면 0-bootstrap 의 eks_control_plane_log_group 이 /aws/eks/<cluster_name>/cluster 와 같아야 한다. 0-bootstrap 을 먼저 apply 했는지, cluster_name 을 바꿨는지 확인하라."
    }
  }

  depends_on = [aws_iam_role_policy_attachment.cluster_eks]
}

# ── ③ OIDC 프로바이더 (IRSA 토대) ──────────────────────────────
data "tls_certificate" "oidc" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "oidc" {
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.oidc.certificates[0].sha1_fingerprint]
}
