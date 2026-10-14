# ── IRSA — Fluent Bit(로그 수집기)용 파드 IAM 역할 (Stage 5d) ─────────────────
#
# 원리는 irsa-eso.tf 와 같다. 다른 점은 둘이다.
#   ① 이 역할을 맡는 ServiceAccount 가 **둘**이다 — 컨테이너 로그(DaemonSet) · K8s 이벤트(Deployment).
#      같은 차트를 두 번 설치하므로 SA 도 둘이고, 쓰는 권한이 같아서 역할은 하나로 둔다.
#   ② 권한은 "로그 줄을 써 넣기"뿐이다. 🔴 **CreateLogGroup 을 주지 않는다** — 그룹은 0-bootstrap 이 만든다.
#      수집기 설정에서 auto_create_group 을 빠뜨려도 여기서 막혀, state 밖에 보존 무기한 그룹이 생길 길이 없다.

locals {
  # 🔴 k8s/logging/fluent-bit.yaml · fluent-bit-events.yaml 의 serviceAccount.name 과 글자 단위로 같아야 한다.
  #    helm install 의 -n 값도 이 네임스페이스여야 한다. 불일치 = IRSA 최다 실패 원인(irsa-eso.tf 주석).
  fluent_bit_namespace = "logging"
  fluent_bit_service_accounts = [
    "fluent-bit",
    "fluent-bit-events",
  ]

  # 그룹 이름은 0-bootstrap 이 소유한다. ARN 은 여기서 조립한다(0-bootstrap 은 이름만 내보낸다 — 계정 ID).
  fluent_bit_log_group_arns = [
    for name in values(data.terraform_remote_state.bootstrap.outputs.learning_log_groups) :
    "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:${name}"
  ]
}

data "aws_iam_policy_document" "fluent_bit_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.oidc.arn]
    }

    # sub — 두 SA 만. 값이 여러 개면 그중 하나와 같으면 통과한다(StringEquals 의 OR).
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_host}:sub"
      values   = [for sa in local.fluent_bit_service_accounts : "system:serviceaccount:${local.fluent_bit_namespace}:${sa}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# ASCII 만 (irsa-eso.tf 의 aws_iam_role.eso 주석 — IAM description 은 한글 불가)
# 의미: Fluent Bit 이 학습용 로그 그룹 2개에 로그를 쓰기 위한 IRSA 역할
resource "aws_iam_role" "fluent_bit" {
  name               = "${var.cluster_name}-fluent-bit"
  description        = "IRSA role for Fluent Bit to write to the learning log groups"
  assume_role_policy = data.aws_iam_policy_document.fluent_bit_trust.json
}

data "aws_iam_policy_document" "fluent_bit_write_logs" {
  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:DescribeLogStreams",
      "logs:PutLogEvents",
    ]
    # `<그룹 ARN>:*` — 그룹 안의 로그 스트림. 스트림 이름은 파드가 뜰 때마다 새로 생기므로(네임스페이스.파드.컨테이너)
    # 미리 나열할 수 없다. 와일드카드는 **그룹 2개 안쪽**으로만 열린다.
    resources = [for arn in local.fluent_bit_log_group_arns : "${arn}:*"]
  }
}

# tfsec:ignore:aws-iam-no-policy-wildcards — 로그 스트림 이름은 파드마다 새로 생겨 미리 나열할 수 없다. 와일드카드 범위는 0-bootstrap 의 학습용 로그 그룹 2개 안쪽뿐이다
resource "aws_iam_policy" "fluent_bit_write_logs" {
  name        = "${var.cluster_name}-fluent-bit-write-logs"
  description = "Fluent Bit write-only access to the learning CloudWatch log groups (no CreateLogGroup)"
  policy      = data.aws_iam_policy_document.fluent_bit_write_logs.json
}

resource "aws_iam_role_policy_attachment" "fluent_bit_write_logs" {
  role       = aws_iam_role.fluent_bit.name
  policy_arn = aws_iam_policy.fluent_bit_write_logs.arn
}
