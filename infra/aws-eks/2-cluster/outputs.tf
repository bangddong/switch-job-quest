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

# ── RDS / 시크릿 (⑧⑨⑩) ────────────────────────────────────────

output "db_address" {
  description = <<-EOT
    RDS 엔드포인트 호스트 (포트 없음 — application-prod.yml의 jdbc-url이 포트를 포함하지 않는다).
    in-cluster 모드에선 **null** — DB가 AWS 리소스가 아니라 K8s Service(`postgres`)이기 때문.
  EOT
  # one(): count=0이면 null. `[0]` 인덱스는 에러가 나므로 조건부 리소스 출력엔 이 관용구를 쓴다.
  value = one(aws_db_instance.main[*].address)
}

output "db_connection_secret_name" {
  description = "DB 접속 좌표(host/dbname) 시크릿 이름 — ExternalSecret의 remoteRef.key에 사용"
  value       = aws_secretsmanager_secret.db_connection.name
}

output "db_master_secret_arn" {
  description = <<-EOT
    RDS가 스스로 만든 마스터 크리덴셜 시크릿 ARN (username/password).
    ExternalSecret의 remoteRef.key에 이 **ARN 또는 이름**을 넣어야 한다.
    이름은 rds!db-... 형태로 AWS가 정하므로 apply 후 이 출력으로 확인할 것.
    🔑 in-cluster 모드에선 **null**이고, 그래서 PLACEHOLDER 치환 절차 자체가 없어진다
       (`k8s/eso/externalsecret-db-incluster.yaml`은 sed 없이 그대로 apply된다).
  EOT
  value       = one(aws_db_instance.main[*].master_user_secret[0].secret_arn)
}

output "app_secret_name" {
  description = "앱 시크릿(JWT·GitHub OAuth) 이름 — ExternalSecret의 remoteRef.key에 사용"
  value       = aws_secretsmanager_secret.app.name
}

output "eso_role_arn" {
  description = <<-EOT
    ESO ServiceAccount에 annotation으로 달 IRSA 역할 ARN.
    helm install 시:
      --set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"=<이 값>
  EOT
  value       = aws_iam_role.eso.arn
}
