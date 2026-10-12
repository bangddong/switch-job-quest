# ⑭ Karpenter 컨트롤러용 IRSA (Stage 5b)
# 계획서: docs/superpowers/plans/2026-10-12-eks-stage5b-karpenter.md
#
# Karpenter 는 클러스터 안에서 도는 파드인데 **EC2 API 를 직접 부른다**(인스턴스 생성·종료,
# 시작 템플릿, 인스턴스 프로파일). 그 권한을 파드에 주는 방법이 IRSA 다 — ALB 컨트롤러·EBS CSI·ESO 와
# 같은 방식이고, OIDC 공급자(cluster.tf)는 이미 있다.
#
# 🔑 권한 정책은 **공식 CloudFormation 템플릿(v1.14)에서 옮겼다.** 손으로 줄이지 않았다.
#    공식 6종 중 4종만 쓴다:
#      node-lifecycle      EC2 생성·태깅·종료. 전부 "이 클러스터 태그가 붙은 것"으로 조건이 걸려 있다
#      iam-integration     노드 역할을 EC2 에 넘기기(PassRole) + 인스턴스 프로파일 생성·삭제
#      eks-integration     클러스터 엔드포인트 조회
#      resource-discovery  서브넷·보안그룹·AMI·가격 조회 (읽기 전용)
#    뺀 것: interruption(스팟 중단 알림 큐 — 큐를 만들지 않는다) · zonal-shift(쓰지 않는다)
#
# 🔑 노드 역할은 **새로 만들지 않고 관리형 노드그룹의 것을 같이 쓴다**(iam.tf 의 aws_iam_role.node).
#    관리형 노드그룹이 그 역할의 access entry 를 이미 만들기 때문에, Karpenter 가 띄운 노드도
#    추가 리소스 없이 클러스터에 붙는다. 공식 안내는 전용 역할을 만든다.
#
# ⚠️ 이 파일은 권한만 만든다. Karpenter 자체는 helm 으로 설치한다(k8s/karpenter/).
#    이 레포의 관례: AWS 리소스는 tofu, 클러스터 안 오브젝트는 kubectl·helm.

locals {
  karpenter_namespace       = "kube-system"
  karpenter_service_account = "karpenter" # 차트 기본값 = 릴리스 이름

  karpenter_policies = toset([
    "node-lifecycle",
    "iam-integration",
    "eks-integration",
    "resource-discovery",
  ])
}

data "aws_iam_policy_document" "karpenter_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.oidc.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_host}:sub"
      values   = ["system:serviceaccount:${local.karpenter_namespace}:${local.karpenter_service_account}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "karpenter" {
  name               = "${var.cluster_name}-karpenter"
  description        = "IRSA role for the Karpenter controller"
  assume_role_policy = data.aws_iam_policy_document.karpenter_trust.json
}

resource "aws_iam_policy" "karpenter" {
  for_each = local.karpenter_policies

  name        = "${var.cluster_name}-karpenter-${each.key}"
  description = "Karpenter controller policy (official v1.14 template): ${each.key}"

  # jsondecode → jsonencode: 템플릿의 들여쓰기·줄바꿈을 걷어 낸다(관리형 정책은 6,144자 제한).
  policy = jsonencode(jsondecode(templatefile(
    "${path.module}/iam-policy-karpenter/${each.key}.json.tftpl",
    {
      region        = var.region
      account_id    = data.aws_caller_identity.current.account_id
      cluster_name  = var.cluster_name
      node_role_arn = aws_iam_role.node.arn
    }
  )))
}

resource "aws_iam_role_policy_attachment" "karpenter" {
  for_each = local.karpenter_policies

  role       = aws_iam_role.karpenter.name
  policy_arn = aws_iam_policy.karpenter[each.key].arn
}
