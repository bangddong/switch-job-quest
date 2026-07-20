output "cluster_name" {
  description = "EKS 클러스터명 (kubeconfig 갱신용)"
  value       = aws_eks_cluster.main.name
}

output "cluster_endpoint" {
  description = "클러스터 API 엔드포인트"
  value       = aws_eks_cluster.main.endpoint
}

output "cluster_ca" {
  description = "클러스터 CA 인증서 (base64)"
  value       = aws_eks_cluster.main.certificate_authority[0].data
  sensitive   = true
}

output "oidc_provider_arn" {
  description = "IRSA용 OIDC 프로바이더 ARN (이후 Stage에서 사용)"
  value       = aws_iam_openid_connect_provider.oidc.arn
}
