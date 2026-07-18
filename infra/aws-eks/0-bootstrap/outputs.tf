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
