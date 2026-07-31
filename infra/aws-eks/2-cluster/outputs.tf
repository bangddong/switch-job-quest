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

# ── Stage 3b: static PV 매니페스트에 주입할 값 ────────────────────
#
# K8s 매니페스트는 terraform을 모른다. 이 레포의 확립된 전달 방식은 **sed 치환**이다
# (core-api.yaml의 IMAGE_PLACEHOLDER, Stage 2 externalsecret의 RDS_MASTER_SECRET_PLACEHOLDER).
# 같은 패턴을 그대로 쓴다 — 새 개념을 늘리지 않는다.
#
#   sed -e "s|EBS_VOLUME_ID_PLACEHOLDER|$(tofu output -raw postgres_data_volume_id)|" \
#       -e "s|PERSISTENT_AZ_PLACEHOLDER|$(tofu output -raw persistent_az)|" \
#       k8s/base/postgres-static.yaml | kubectl apply -f -
#
# ℹ️ 값 자체는 0-bootstrap이 소유한다. 여기서는 **중계만** 한다 —
#    세션 작업 디렉토리가 2-cluster라, 사람이 레이어를 오가지 않아도 되게 하려는 것뿐이다.

output "postgres_data_volume_id" {
  description = "영속 EBS 볼륨 ID (static PV의 volumeHandle). 0-bootstrap 소유값의 중계."
  value       = data.terraform_remote_state.bootstrap.outputs.postgres_data_volume_id
}

output "persistent_az" {
  description = "영속 EBS·노드가 함께 놓인 AZ (static PV의 nodeAffinity). 0-bootstrap 소유값의 중계."
  value       = data.terraform_remote_state.bootstrap.outputs.persistent_az
}
