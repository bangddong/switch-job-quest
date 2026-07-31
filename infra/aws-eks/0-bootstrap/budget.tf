# 누적 크레딧 소진 알림. 콘솔 수동 예산(부트스트랩 프롤로그)을 코드로 승격한 것.
#
# 개념:
# - AWS Budgets = limit_amount(기준 금액) + 여러 notification(알림).
# - cost_types.include_credit = false: 크레딧 적용 "전" 실요금으로 측정한다.
#   그 요금을 크레딧이 대신 내므로 **측정값 = 소진된 크레딧액**이 된다(정의상 동일).
# - threshold_type = ABSOLUTE_VALUE: 임계값을 %가 아닌 실제 달러로 해석
#   (콘솔 기본값 PERCENTAGE는 $200 기준 $20/$100/$300으로 어긋나는 함정 — 절대값으로 고정).
# - time_unit = ANNUALLY + time_period_start: 매달 리셋되지 않고 **누적**된다.
#   왜 월간을 버렸는지는 variables.tf의 budget_period_start 주석 참조.

locals {
  # 🔴 **AWS 하드 리밋 — 예산 1개당 알림 10개.** (2026-07-31 실측, 프로브 후 삭제)
  #   생성 시 11개 이상 주면:
  #     ValidationException: Value at 'notificationsWithSubscribers' failed to satisfy
  #     constraint: Member must have length less than or equal to 10
  #   만들어 놓고 나중에 추가해도:
  #     CreationLimitExceededException: one budget can only have 10 notifications
  #   → 호출당이 아니라 **총계** 상한이라 "나눠서 여러 번 호출" 우회가 없다. 예산을 쪼갠다.
  #
  # ⚠️ 이 상한은 `tofu plan`이 못 잡는다 — plan은 AWS API를 부르지 않는다.
  #    초과하면 **main 머지 시 CI apply가 깨진다**(infra-deploy.yml이 이 레이어를 자동 apply).
  #    한글 description · EMAIL+IMMEDIATE · 계정당 이상탐지 1개에 이은 **같은 계열 4번째 함정**이라,
  #    사람이 세는 대신 chunklist로 구조적으로 못 넘게 만든다.
  budget_notifications_max = 10

  # $10 · $20 · … · $200  (step=10, total=200 → 20단계)
  credit_thresholds = [
    for i in range(1, ceil(var.credit_total_usd / var.budget_alert_step_usd) + 1) :
    i * var.budget_alert_step_usd
  ]

  # 10개씩 끊어 예산 여러 개로 나눈다.
  # step을 $5로 바꾸면 40단계 → 예산 4개로 **자동 확장**된다(이 파일 수정 불필요).
  credit_threshold_chunks = chunklist(local.credit_thresholds, local.budget_notifications_max)
}

resource "aws_budgets_budget" "credit_burn" {
  count = length(local.credit_threshold_chunks)

  # 이름에 담당 구간을 박는다 — 알림 메일만 보고 어느 단계인지 바로 알 수 있게.
  # 0 패딩은 콘솔·CLI 목록에서 사전순 정렬이 숫자순과 일치하게 만든다(010 < 110).
  name = format(
    "devquest-eks-credit-%03d-%03d",
    local.credit_threshold_chunks[count.index][0],
    local.credit_threshold_chunks[count.index][length(local.credit_threshold_chunks[count.index]) - 1],
  )

  budget_type  = "COST"
  limit_amount = var.budget_limit_usd
  limit_unit   = "USD"

  # ANNUALLY = 1년 주기. 시작일을 크레딧 창이 열린 달로 잡아 그 이후를 누적한다.
  # 실측(2026-07-31): 과거 시작일도 그대로 보존되며 조정되지 않는다
  #   Start 2026-07-01T09:00:00+09:00 / End 2087-... (미지정 시 기본 무한대)
  time_unit         = "ANNUALLY"
  time_period_start = var.budget_period_start

  # 크레딧·환불을 빼고 순수 사용 요금으로 측정 (나머지 비용 유형은 기본 포함)
  cost_types {
    include_credit = false
    include_refund = false
  }

  # 임계값마다 알림 하나 — ACTUAL(실제 발생) 비용이 절대값 초과 시 이메일 발송.
  # ⚠️ FORECASTED가 아니라 ACTUAL이다. 예측 알림은 사용 패턴이 튀는 학습장에서 오탐이 잦고,
  #    오탐이 잦은 알림은 곧 무시된다 — 그러면 진짜 신호도 함께 죽는다.
  dynamic "notification" {
    for_each = local.credit_threshold_chunks[count.index]
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "ABSOLUTE_VALUE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.budget_notification_email]
    }
  }
}
