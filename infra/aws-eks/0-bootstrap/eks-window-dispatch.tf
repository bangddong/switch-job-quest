# ── EKS 창 자동 off · watchdog — AWS 가 정시에 GitHub 워크플로를 부른다 (📌D-016 B4-21, 2026-10-23) ──
#
# 왜: 18:00 off 와 매시 watchdog(= CI 가 켠 클러스터의 dead man's switch)이 **GitHub cron** 이었는데,
#     10-22 실측에서 18:00 슬롯이 **5시간 24분 늦게** 돌았고 매시 watchdog 은 **10슬롯 중 1개만 생성**됐다.
#     같은 레포 cron 도 9월 이후 5.5~9시간 늦다. 반면 workflow_dispatch 는 **수 초** 안에 시작됐다.
#     → 시각은 AWS 가 지키고, 끄는 일은 지금 워크플로(eks-window.yml off 잡)가 그대로 한다.
#
#   [scheduled rule] ──정시──▶ [API destination] ──POST /actions/workflows/eks-window.yml/dispatches──▶ GitHub
#                                    │ 인증 = [connection] (헤더 Authorization: Bearer <fine-grained PAT>)
#
# 🔑 설계 선택
#   · **EventBridge Scheduler 가 아니라 기본 버스의 scheduled rule** 이다. Scheduler 는 API destination 을 직접 부르지
#     못한다(대상 = AWS API 뿐) — 쓰려면 Scheduler → 버스 → rule → API destination 으로 한 단을 더 거친다.
#     AWS 는 scheduled rule 을 "legacy" 라 부르지만 지원은 유지되고, 시각은 UTC 다(GitHub cron 과 같다).
#   · **PAT 는 레포·tofu state 어디에도 없다.** connection 을 placeholder 로 만들고 `ignore_changes` 로 손을 뗀다.
#     실제 값은 사람이 AWS 콘솔에서 넣는다(EventBridge → Connections → 편집). 넣은 값은 EventBridge 가 만든
#     Secrets Manager 시크릿(`events!connection/...`)에 산다 — state 밖·태그 없음(PERSISTENT-RESOURCES.md).
#   · PAT 권한 = **이 레포 하나 · Actions: Read and write 하나**(fine-grained). 🔴 반경은 eks-window 만이 아니다 —
#     이 레포의 **모든 workflow_dispatch 워크플로**(ecr-push·claude-batch-fix·prod-smoke-daily·prod-db-backup-watch 등)를
#     main 에서 실행할 수 있고, 실행 취소·재실행·로그 삭제도 된다(QA F-3). 예산 게이트(budget-gate.sh)는 eks-window on 만 막는다.
#     코드는 못 바꾼다(Contents 권한 없음) — 돌릴 수 있는 것은 main 에 이미 있는 정의뿐이다. ⚠️ main 역할(Admin)로 도는 워크플로는 이 시크릿을 읽을 수
#     있다(PR plan 역할은 DenyContentReads 로 못 읽는다) — 원래 AWS 전권을 가진 역할이라 경계가 새로 넓어지진 않는다.
#
# 🔴 connection 의 **name 을 바꾸지 마라.** 이름 변경 = 교체(replace) → 콘솔에서 넣은 PAT 가 **조용히 placeholder 로
#    돌아간다**(`ignore_changes` 는 교체를 못 막는다). 그 뒤 dispatch 는 401 → 아래 알람이 메일로 알린다.
# ⚠️ 이 경로도 GitHub Actions **장애**에는 함께 걸린다(dispatch 는 받아도 run 이 안 돌 수 있다). B4-21 이 해소한 것은
#    cron 지연·누락·60일 비활성이다. dispatch 실패(401·404·422·5xx)는 알람이 잡고, run 실패는 GitHub 메일 몫이다.
# ⚠️ 로컬에서 2-cluster 를 켜려고 EKS Window 워크플로를 Disable 하면 이 규칙의 dispatch 가 매시 실패해 알람이 반복된다
#    → 규칙도 함께 끈다(`aws events disable-rule --name devquest-eks-window-watchdog` · `...-off`). 다음 0-bootstrap
#    apply 가 다시 켠다(state = ENABLED) — 의도된 동작이다. 절차: scripts/prod-window/README.md.

locals {
  eks_window_repo = "bangddong/switch-job-quest"

  # 🔴 input 의 키는 GitHub API 형식 그대로 `ref` · `inputs.action` 이다. 오타가 나면 워크플로 기본값이 쓰인다 —
  #    그래서 eks-window.yml 의 action 기본값을 "off" 로 바꿨다(켜기 쪽으로 틀리지 않게, Blindspot U1).
  # max_age = GitHub 이 5xx·429 를 줄 때 재시도하는 최대 시간. 기본 24h 면 **몇 시간 늦은 force off 가 18:00 이후
  #    사람이 켠 창을 끈다**(Blindspot U4) — off 는 30분, watchdog 은 다음 회차 전(15분)에 접는다.
  eks_window_dispatch = {
    off = {
      schedule    = "cron(0 9 * * ? *)" # 18:00 KST — 매일(주말 포함). 꺼져 있으면 no-op(Q-3)
      action      = "off"
      max_age     = 1800
      description = "D-016 EKS window - 18:00 KST force off via GitHub workflow_dispatch"
    }
    watchdog = {
      schedule    = "cron(17 * * * ? *)" # 매시 17분 — 임대 없거나 만료일 때만 끈다
      action      = "watchdog"
      max_age     = 900
      description = "D-016 EKS window - hourly watchdog (off only if lease missing/expired)"
    }
  }
}

resource "aws_cloudwatch_event_connection" "github" {
  name               = "devquest-eks-window-github" # 🔴 바꾸지 마라 — 위 머리말
  description        = "GitHub fine-grained PAT (this repo, Actions RW) - value set in console, not in state"
  authorization_type = "API_KEY"

  auth_parameters {
    api_key {
      key   = "Authorization"
      value = "Bearer set-in-console" # placeholder — 실제 PAT 는 콘솔에서. 이 값이면 dispatch 는 401
    }
  }

  lifecycle {
    ignore_changes = [auth_parameters]
  }
}

resource "aws_cloudwatch_event_api_destination" "eks_window_dispatch" {
  name                             = "devquest-eks-window-dispatch"
  description                      = "POST workflow_dispatch for eks-window.yml"
  invocation_endpoint              = "https://api.github.com/repos/${local.eks_window_repo}/actions/workflows/eks-window.yml/dispatches"
  http_method                      = "POST"
  invocation_rate_limit_per_second = 1
  connection_arn                   = aws_cloudwatch_event_connection.github.arn
}

resource "aws_cloudwatch_event_rule" "eks_window" {
  for_each = local.eks_window_dispatch

  name                = "devquest-eks-window-${each.key}"
  description         = each.value.description
  schedule_expression = each.value.schedule
}

resource "aws_cloudwatch_event_target" "eks_window" {
  for_each = local.eks_window_dispatch

  rule      = aws_cloudwatch_event_rule.eks_window[each.key].name
  target_id = "github-dispatch"
  arn       = aws_cloudwatch_event_api_destination.eks_window_dispatch.arn
  role_arn  = aws_iam_role.eks_window_dispatch.arn

  # 상수 JSON — 원래 이벤트(스케줄 메타데이터)는 보내지 않는다. GitHub 응답 = 204 No Content
  input = jsonencode({
    ref    = "main"
    inputs = { action = each.value.action }
  })

  http_target {
    header_parameters = {
      "Accept"               = "application/vnd.github+json"
      "X-GitHub-Api-Version" = "2022-11-28"
    }
  }

  retry_policy {
    maximum_event_age_in_seconds = each.value.max_age
    maximum_retry_attempts       = 20
  }
}

# 규칙이 API destination 을 부를 권한 — 그것 하나만
data "aws_iam_policy_document" "eks_window_dispatch_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "eks_window_dispatch" {
  name               = "devquest-eks-window-dispatch"
  description        = "EventBridge rules -> API destination (GitHub workflow_dispatch for eks-window)"
  assume_role_policy = data.aws_iam_policy_document.eks_window_dispatch_assume.json
}

data "aws_iam_policy_document" "eks_window_dispatch_invoke" {
  statement {
    actions   = ["events:InvokeApiDestination"]
    resources = [aws_cloudwatch_event_api_destination.eks_window_dispatch.arn]
  }
}

resource "aws_iam_role_policy" "eks_window_dispatch_invoke" {
  name   = "invoke-github-dispatch"
  role   = aws_iam_role.eks_window_dispatch.id
  policy = data.aws_iam_policy_document.eks_window_dispatch_invoke.json
}

# ── 실패 알림 — "조용히 안 돈다" 를 메일로 바꾼다 ──────────────────────────────────────────
# FailedInvocations = 재시도까지 끝내고 **배달에 실패한** 호출 수(401 PAT 만료·422 입력 오류·404 워크플로 Disable 등).
# 💰 표준 알람 2개(무료 한도 10개 안) · SNS 이메일(무료 한도 안).
# ⚠️ 이메일 구독은 받은 메일의 확인 링크를 누르기 전까지 **pending** 이라 알람이 울려도 메일이 안 온다 —
#    budget·anomaly 는 SNS 없이 메일로 직접 보내 이 단계가 없었다. apply 직후 확인 링크를 누른다(TASKS).

# tfsec:ignore:aws-sns-enable-topic-encryption — 내용은 알람 이름·상태뿐(시크릿 없음). AWS 관리형 키(alias/aws/sns)로 암호화하면 CloudWatch 알람이 publish 하지 못해 알림이 조용히 죽는다(AWS 문서). CMK 는 월 $1+ — log-groups.tf · ecr.tf 의 KMS 생략과 같은 판단
resource "aws_sns_topic" "eks_window_alarm" {
  name = "devquest-eks-window-alarm"
}

resource "aws_sns_topic_subscription" "eks_window_alarm_email" {
  topic_arn = aws_sns_topic.eks_window_alarm.arn
  protocol  = "email"
  endpoint  = var.budget_notification_email
}

resource "aws_cloudwatch_metric_alarm" "eks_window_dispatch_failed" {
  for_each = local.eks_window_dispatch

  alarm_name          = "devquest-eks-window-${each.key}-dispatch-failed"
  alarm_description   = "EventBridge -> GitHub dispatch failed (${each.key}). Check PAT expiry / workflow enabled / inputs."
  namespace           = "AWS/Events"
  metric_name         = "FailedInvocations"
  dimensions          = { RuleName = aws_cloudwatch_event_rule.eks_window[each.key].name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching" # 실패가 없으면 데이터 자체가 없다 — 그게 정상
  alarm_actions       = [aws_sns_topic.eks_window_alarm.arn]
}
