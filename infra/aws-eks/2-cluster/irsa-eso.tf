# ── ⑩ IRSA — External Secrets Operator용 파드 IAM 역할 ───────────
#
# ★ 이 파일이 이번 Stage의 핵심 개념이다.
#
# IRSA(IAM Roles for Service Accounts)가 푸는 문제:
#   기존 방식은 **노드**에 IAM 역할을 붙이는 것이었다(Stage 1에서 ECR pull이
#   그렇게 동작했다 — nodes.tf의 AmazonEC2ContainerRegistryReadOnly).
#   그런데 노드 역할은 **그 노드 위의 모든 파드가 공유**한다. Secrets Manager
#   읽기 권한을 노드에 주면 같은 노드의 아무 파드나 시크릿을 읽을 수 있다.
#   → 권한 경계가 "노드"라서 최소권한이 성립하지 않는다.
#
# IRSA는 경계를 **ServiceAccount 단위**로 좁힌다. 동작 순서:
#   ① EKS가 클러스터마다 OIDC 발급자(issuer)를 갖는다 (cluster.tf ③에서 IAM에 등록)
#   ② 파드의 ServiceAccount에 역할 ARN을 annotation으로 달면, kubelet이 그
#      ServiceAccount의 **서명된 JWT 토큰**을 파드 안에 파일로 마운트한다
#   ③ AWS SDK가 그 토큰으로 sts:AssumeRoleWithWebIdentity를 호출한다
#   ④ STS가 OIDC 발급자에게 서명을 검증하고, 아래 신뢰정책의 `sub` 조건
#      (= 어느 네임스페이스의 어느 ServiceAccount인지)이 일치할 때만 임시 자격증명을 준다
#   → **장기 액세스키가 어디에도 없다.** 0-bootstrap의 GitHub Actions OIDC와 같은 원리다.

locals {
  # 신뢰정책 조건 키는 "https://" 접두어 없는 발급자 호스트+경로 형태여야 한다.
  # 예: oidc.eks.ap-northeast-2.amazonaws.com/id/ABCD...
  oidc_issuer_host = replace(aws_iam_openid_connect_provider.oidc.url, "https://", "")

  # ESO를 설치할 네임스페이스/ServiceAccount 이름.
  # 여기 값과 실제 helm install 시 지정하는 값이 **정확히 일치해야** ④의 sub 조건이 맞는다.
  # 불일치가 IRSA 최다 실패 원인이다 (증상: 파드 로그에
  #  "Not authorized to perform sts:AssumeRoleWithWebIdentity" — Stage 1에서 GitHub OIDC로 겪은 것과 동일).
  eso_namespace       = "external-secrets"
  eso_service_account = "external-secrets"
}

data "aws_iam_policy_document" "eso_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.oidc.arn]
    }

    # sub — "누가" 이 역할을 맡을 수 있는지. 이 한 줄이 권한 경계를 파드 수준으로 좁힌다.
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_host}:sub"
      values   = ["system:serviceaccount:${local.eso_namespace}:${local.eso_service_account}"]
    }

    # aud — 토큰 수신자 확인. 빼먹으면 다른 대상용으로 발급된 토큰도 통과할 수 있어
    # confused deputy 위험이 생긴다. AWS 문서가 항상 함께 걸라고 명시하는 조건.
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eso" {
  name               = "${var.cluster_name}-eso"
  description        = "External Secrets Operator가 Secrets Manager를 읽기 위한 IRSA 역할"
  assume_role_policy = data.aws_iam_policy_document.eso_trust.json
}

# 권한은 **이 클러스터가 만든 시크릿 3개로만** 한정한다.
# 와일드카드(`secretsmanager:*` 또는 Resource "*")를 쓰지 않는 이유는 두 가지다:
#   ① 최소권한 — ESO가 계정 안 모든 시크릿을 읽을 이유가 없다
#   ② tfsec `aws-iam-no-policy-wildcards`가 hard fail로 CI를 막는다
#      (.github/workflows/infra-ci.yml — soft_fail 미지정)
data "aws_iam_policy_document" "eso_read_secrets" {
  statement {
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      aws_secretsmanager_secret.db_connection.arn,
      aws_secretsmanager_secret.app.arn,
      # RDS가 스스로 만든 마스터 크리덴셜 시크릿.
      # tofu가 만든 게 아니라 RDS가 만든 것이라, 이렇게 참조로만 잡을 수 있다.
      aws_db_instance.main.master_user_secret[0].secret_arn,
    ]
  }
}

resource "aws_iam_policy" "eso_read_secrets" {
  name        = "${var.cluster_name}-eso-read-secrets"
  description = "ESO: 이 클러스터용 Secrets Manager 시크릿 3개 읽기 전용"
  policy      = data.aws_iam_policy_document.eso_read_secrets.json
}

resource "aws_iam_role_policy_attachment" "eso_read_secrets" {
  role       = aws_iam_role.eso.name
  policy_arn = aws_iam_policy.eso_read_secrets.arn
}
