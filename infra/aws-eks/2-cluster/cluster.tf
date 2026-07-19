# ── ② EKS 컨트롤플레인 ─────────────────────────────────────────
# tfsec 근거 ignore:
#   public 엔드포인트 개방은 단독 데스크톱 학습 놀이터의 의도된 선택
#   (인증은 IAM + Access Entry로 게이트). prod면 CIDR 제한/private.
#   control-plane 로깅·secret KMS 암호화는 CloudWatch·KMS 비용 회피로 생략.
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
