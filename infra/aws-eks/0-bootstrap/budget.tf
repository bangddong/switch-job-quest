# 월 예산 + 다단계 알림. 콘솔 수동 예산(부트스트랩 프롤로그)을 코드로 승격한 것.
#
# 개념:
# - AWS Budgets = limit_amount(기준 금액) + 여러 notification(알림).
# - cost_types.include_credit = false: $200 크레딧이 실사용을 가리지 않도록, 크레딧 적용 "전"
#   실요금 기준으로 측정 → 크레딧이 남아도 실제 $10/$50/$150 쓰면 알림 발동 (학습장 가드레일).
# - threshold_type = ABSOLUTE_VALUE: 임계값을 %가 아닌 실제 달러로 해석
#   (콘솔 기본값 PERCENTAGE는 $200 기준 $20/$100/$300으로 어긋나는 함정 — 절대값으로 고정).
resource "aws_budgets_budget" "monthly" {
  name         = "devquest-eks-monthly"
  budget_type  = "COST"
  limit_amount = var.budget_limit_usd
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # 크레딧·환불을 빼고 순수 사용 요금으로 측정 (나머지 비용 유형은 기본 포함)
  cost_types {
    include_credit = false
    include_refund = false
  }

  # 임계값마다 알림 하나 — ACTUAL(실제 발생) 비용이 절대값 초과 시 이메일 발송
  dynamic "notification" {
    for_each = var.budget_alert_thresholds_usd
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "ABSOLUTE_VALUE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.budget_notification_email]
    }
  }
}
