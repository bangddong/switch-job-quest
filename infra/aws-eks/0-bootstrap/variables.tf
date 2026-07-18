variable "region" {
  type        = string
  description = "AWS 리전 (서울)"
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  type        = string
  description = "OpenTofu remote state용 S3 버킷 이름 (전역 유일해야 함 — 충돌 시 변경)"
  default     = "devquest-eks-tfstate-seoul"
}

variable "lock_table_name" {
  type        = string
  description = "state 락용 DynamoDB 테이블 이름"
  default     = "devquest-eks-tflock"
}

# ⚠️ public repo: 이메일은 default 금지 — 값은 gitignore되는 terraform.tfvars에 둔다.
variable "budget_notification_email" {
  type        = string
  description = "예산 알림 수신 이메일 (terraform.tfvars에 값 지정)"
  sensitive   = true
}

variable "budget_limit_usd" {
  type        = string
  description = "월 예산 기준 금액 (USD). 크레딧 총액 $200 기준."
  default     = "200"
}

variable "budget_alert_thresholds_usd" {
  type        = list(number)
  description = "절대값(USD) 알림 임계값 — 크레딧 제외 실사용 기준"
  default     = [10, 50, 150]
}

# 공개 정보(레포 URL) — default 허용. OIDC 신뢰정책의 sub 조건에 쓰인다.
variable "github_repo" {
  type        = string
  description = "GitHub Actions가 이 역할을 assume할 수 있는 레포 (owner/repo)"
  default     = "bangddong/switch-job-quest"
}
