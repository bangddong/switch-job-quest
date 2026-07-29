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

# 🔴 이 두 리소스는 **새로 만드는 게 아니라 이미 있는 것을 인수(import)한 것이다.**
#    (07-29 apply 실패로 알게 됨 — 아래가 그 에러다)
#
#      Error: creating Cost Explorer Anomaly Monitor: ValidationException:
#      Limit exceeded on dimensional spend monitor creation
#
#    원인: **AWS가 신규 계정에 `Default-Services-Monitor`를 미리 만들어 둔다.** 그리고
#    DIMENSIONAL(SERVICE) 모니터는 **계정당 1개만** 허용된다 → 같은 걸 또 만들 수 없다.
#    `tofu plan`은 이걸 못 잡는다(기존 리소스를 모르니 그냥 "2 to add"라고 한다).
#
#    → 해결: 로컬에서 state로 인수한 뒤 우리 값으로 갱신했다.
#      tofu import aws_ce_anomaly_monitor.services      <monitor-arn>
#      tofu import aws_ce_anomaly_subscription.alerts   <subscription-arn>
#      (ARN은 `aws ce get-anomaly-monitors` / `get-anomaly-subscriptions`로 조회. 계정ID 포함이라 레포에 안 적는다.)
#
#    ⚠️ 다른 계정에서 재현할 때도 **똑같이 import부터** 해야 한다. UUID가 계정마다 달라
#    `import` 블록으로 코드화해도 이식되지 않으므로, 이 단계는 사람이 한 번 하는 게 맞다.

# 무엇을 감시할지 — SERVICE 차원으로 계정 내 모든 서비스를 각각 감시한다.
# (monitor_type=CUSTOM으로 특정 태그/계정만 볼 수도 있으나, 학습 계정은 "전부"가 맞다:
#  어떤 서비스가 샐지 모르는 게 애초에 문제이므로 감시 범위를 좁히면 목적을 잃는다.)
# name만 우리 것으로 바꾼다 — 이름 변경은 in-place라 안전하다(재생성이면 위 한도에 또 걸린다).
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
  #
  # 🔴 **AWS 기본값을 그대로 두면 안 되는 이유** (07-29 실측): 인수해 온 기본 구독은
  #    `$100 이상 AND 40% 이상`이었다. 크레딧 총액이 $200인 학습 계정에서
  #    **절반이 날아간 뒤에야 울리는** 값이라 사실상 꺼진 것과 같다.
  #    "기본값이 있으니 됐다"가 가장 위험한 상태 — 켜져 있는데 안 울린다.
  threshold_expression {
    dimension {
      key           = "ANOMALY_TOTAL_IMPACT_ABSOLUTE"
      match_options = ["GREATER_THAN_OR_EQUAL"]
      values        = [var.anomaly_threshold_usd]
    }
  }
}
