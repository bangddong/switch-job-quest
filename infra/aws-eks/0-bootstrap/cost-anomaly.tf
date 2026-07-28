# Cost Anomaly Detection — 예산 알림이 못 잡는 것을 잡는 2차 가드레일.
#
# 예산(budget.tf)과 무엇이 다른가:
# - 예산   = "절대 금액"을 넘으면 알림. $10을 넘어야 울린다.
# - 이상탐지 = "평소 패턴 대비 튀는가"를 ML로 판정. 금액이 작아도 패턴이 이상하면 울린다.
#   → 학습 계정에서 "안 쓰던 서비스가 갑자기 과금되기 시작"(= 지우는 걸 잊은 리소스)을 잡는 용도.
#
# 💰 비용: Cost Anomaly Detection 자체는 **무료**. 그래서 과금 세션과 무관하게 상시 켜 둔다.
#
# ⚠️ 이건 "실시간 브레이크"가 아니다 (기대치를 정확히 해 둔다):
#   EMAIL 구독자는 IMMEDIATE(개별 즉시 알림)를 못 쓴다 — AWS가 SNS에만 허용한다. 그래서 최선이 DAILY다.
#   즉 이 장치가 알려주는 시점엔 이미 하루치가 과금돼 있다. 30분 세션을 지키는 실시간 장치는
#   리퍼(dead man's switch)이고, 이건 **리퍼까지 실패했을 때의 마지막 그물**이다.

# 무엇을 감시할지 — SERVICE 차원으로 계정 내 모든 서비스를 각각 감시한다.
# (monitor_type=CUSTOM으로 특정 태그/계정만 볼 수도 있으나, 학습 계정은 "전부"가 맞다:
#  어떤 서비스가 샐지 모르는 게 애초에 문제이므로 감시 범위를 좁히면 목적을 잃는다.)
resource "aws_ce_anomaly_monitor" "services" {
  name              = "devquest-eks-service-monitor"
  monitor_type      = "DIMENSIONAL"
  monitor_dimension = "SERVICE"
}

# 감지된 이상을 누구에게 어떻게 보낼지.
resource "aws_ce_anomaly_subscription" "alerts" {
  name             = "devquest-eks-anomaly-alerts"
  monitor_arn_list = [aws_ce_anomaly_monitor.services.arn]

  # 🔴 EMAIL은 DAILY/WEEKLY만 가능. IMMEDIATE는 SNS 전용이다.
  #    (문서 확인 후 확정 — IMMEDIATE + EMAIL 조합은 apply 시점에야 거부당한다)
  frequency = "DAILY"

  subscriber {
    type    = "EMAIL"
    address = var.budget_notification_email
  }

  # 얼마나 튀어야 알릴지. 예산 1단계($10)보다 낮게 잡아 "예산이 울기 전에" 먼저 걸리게 한다.
  # ANOMALY_TOTAL_IMPACT_ABSOLUTE = 이상 구간의 누적 영향액(USD, 절대값).
  threshold_expression {
    dimension {
      key           = "ANOMALY_TOTAL_IMPACT_ABSOLUTE"
      match_options = ["GREATER_THAN_OR_EQUAL"]
      values        = [var.anomaly_threshold_usd]
    }
  }
}
