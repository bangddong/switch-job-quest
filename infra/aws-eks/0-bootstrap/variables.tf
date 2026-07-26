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

# ── ECR ──────────────────────────────────────────────────────────
# 배포 대상 서비스마다 레포 하나. daily-api는 Phase 2 추출 시 추가.
variable "ecr_repositories" {
  type        = list(string)
  description = "생성할 ECR 레포 목록 (devquest/<name> 으로 네이밍). 서비스 분해 대상 앱들."
  default     = ["core-api", "ai-api"]
}

variable "ecr_image_tag_mutability" {
  type        = string
  description = "MUTABLE(학습: 같은 태그 재푸시 허용) 또는 IMMUTABLE(prod 권장)."
  default     = "MUTABLE"

  validation {
    condition     = contains(["MUTABLE", "IMMUTABLE"], var.ecr_image_tag_mutability)
    error_message = "ecr_image_tag_mutability must be MUTABLE or IMMUTABLE."
  }
}

variable "ecr_keep_last_images" {
  type        = number
  description = "레포당 유지할 최근 이미지 수 (초과분은 lifecycle policy로 만료)."
  default     = 10
}
