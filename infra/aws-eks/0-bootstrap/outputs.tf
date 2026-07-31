output "state_bucket" {
  description = "remote state S3 버킷 (backend.tf에 사용)"
  value       = aws_s3_bucket.tfstate.id
}

output "lock_table" {
  description = "state 락 DynamoDB 테이블 (backend.tf에 사용)"
  value       = aws_dynamodb_table.tflock.name
}

output "account_id" {
  description = "현재 AWS 계정 ID (민감 — 로그 노출 방지)"
  value       = data.aws_caller_identity.current.account_id
  sensitive   = true
}

output "github_actions_role_arn" {
  description = "CI 워크플로가 assume할 역할 ARN (apply-plan 파이프라인에서 사용)"
  value       = aws_iam_role.github_actions.arn
}

output "ecr_repository_urls" {
  description = "앱별 ECR 레포 URL (docker push 대상 · K8s 매니페스트 image: 값)"
  value       = { for name, repo in aws_ecr_repository.app : name => repo.repository_url }
}

# ── Stage 3b: 2-cluster가 remote state로 읽어가는 값 ──────────────
#
# 이 두 출력이 "영속 레이어 ↔ 세션 레이어"를 잇는 유일한 통로다.
# 2-cluster는 이 값으로 ①노드그룹을 같은 AZ에 고정하고 ②PV 매니페스트에 볼륨 ID를 주입한다.

output "persistent_az" {
  description = <<-EOT
    영속 EBS가 있는 AZ. 2-cluster의 노드그룹이 이 AZ로 고정된다.
    🔑 이 값의 단일 출처는 var.persistent_az다 — 2-cluster에 같은 변수를 만들지 말 것.
  EOT
  value       = var.persistent_az
}

output "postgres_data_volume_id" {
  description = <<-EOT
    영속 Postgres 데이터 볼륨 ID (static PV의 volumeHandle에 들어간다).
    postgres_persistent_volume_enabled = false면 null — 그 경우 Stage 3b는 성립하지 않는다.
  EOT
  # count 기반 리소스는 [0] 인덱싱이 count=0에서 에러다. one()은 null을 준다(rds.tf와 같은 패턴).
  value = one(aws_ebs_volume.postgres_data[*].id)
}
