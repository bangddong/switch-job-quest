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

# ℹ️ 예산의 limit_amount는 **변수가 아니라 credit_total_usd에서 파생**된다(budget.tf).
#    같은 "$200"을 독립 변수 둘로 두면 크레딧이 바뀔 때 한쪽만 고쳐 어긋난다.
#    (QA F-2 — 지금은 안 틀렸지만 미래에 틀릴 구조였다.)

# ── 누적 크레딧 소진 알림 ($10 단위) ──────────────────────────
#
# 🔴 **왜 월간이 아니라 누적인가 (2026-07-31 변경).**
#   이전 설정은 `time_unit = MONTHLY` + 임계 [10, 50, 150]이었다. 월간 예산은 **매달 0으로
#   리셋**되는데, destroy-after-use 실사용은 월 $14 수준이다. 즉 $10 단위를 월간에 걸면
#   **$10만 거의 매달 울리고 나머지 단계는 평생 안 울린다** — 알림이 신호가 아니라 소음이 된다.
#
#   알고 싶은 것은 "이번 달 얼마 썼나"가 아니라 **"$200 크레딧이 얼마 남았나"** 다.
#   그래야 임계마다 전략을 다시 세울 수 있다(세션 빈도 · 인스턴스 타입 · 막판 상시 데모 여부).
#   → `ANNUALLY` + 크레딧 창 시작일 = 리셋 없는 누적 집계.
#
# ℹ️ **측정값이 곧 크레딧 소진액인 이유**: budget.tf의 `include_credit = false`가 크레딧 적용
#    **전** 실요금을 재는데, 그 요금을 크레딧이 대신 낸다. 따라서 두 값은 정의상 같다.
#    실측 확인(2026-07-31): ANNUALLY/2026-07-01 시작 예산의 ActualSpend = **$0.481**.
variable "budget_period_start" {
  type        = string
  description = "누적 집계 시작 시각 (YYYY-MM-DD_HH:MM). AWS 크레딧 창이 열린 달의 1일."
  default     = "2026-07-01_00:00"
}

variable "credit_total_usd" {
  type        = number
  description = "추적할 크레딧 총액 (USD). 예산 limit_amount이자 마지막 알림 임계값이 된다."
  default     = 200

  # 🔴 0 이하면 credit_thresholds가 빈 리스트 → 예산 리소스 count=0 →
  #    **에러 하나 없이 알림 20개가 통째로 사라진다.** 가드레일이 조용히 없어지는 게
  #    가장 나쁜 실패 모드라, 침묵 대신 apply를 멈추게 한다. (QA F-4)
  validation {
    condition     = var.credit_total_usd > 0
    error_message = "credit_total_usd must be greater than 0 (0 이하면 예산 알림이 조용히 전부 사라진다)."
  }
}

variable "budget_alert_step_usd" {
  type = number
  description = <<-EOT
    알림 간격 (USD). 10이면 $10·$20·…·$200 = 20단계.
    줄이면 단계가 늘고, 예산 1개당 알림 10개 상한 때문에 예산 개수가 자동으로 는다
    (budget.tf의 chunklist가 처리 — 코드 수정 불필요).
    ⚠️ 개수는 비용이 아니다: 알림 전용 예산은 무료다.
       Pricing API 실측(2026-07-31) — BudgetsUsage(Budget Notifications) = $0.00,
       상위 과금 구간 자체가 없다. 유료인 것은 Budget *Actions*(자동 조치형)뿐이며 우리는 안 쓴다.
  EOT
  default = 10

  validation {
    condition     = var.budget_alert_step_usd > 0
    error_message = "budget_alert_step_usd must be greater than 0."
  }

  # 🔴 정수만 허용한다. budget.tf의 예산 이름이 format("%03d", ...)로 만들어지는데
  #    소수를 주면 `an integer is required`로 **plan이 크래시**한다 — 에러 메시지가
  #    step 변수를 가리키지 않아 원인 찾는 데 시간이 든다. 여기서 이름을 대고 막는다.
  #    (QA F-3 — tofu console로 2.5 입력 시 재현 확인)
  validation {
    condition     = floor(var.budget_alert_step_usd) == var.budget_alert_step_usd
    error_message = "budget_alert_step_usd must be a whole number (예산 이름 생성이 %03d 포맷이라 소수는 plan을 깨뜨린다)."
  }
}

# 이상탐지는 예산보다 먼저 울려야 의미가 있다 → budget 1단계($10)보다 낮게.
variable "anomaly_threshold_usd" {
  type        = string
  description = "Cost Anomaly Detection 알림 임계값 (USD, 누적 영향액 절대값)"
  default     = "5"
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
