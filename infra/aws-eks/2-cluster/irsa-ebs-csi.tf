# ── ⑪ IRSA — EBS CSI 드라이버용 IAM 역할 (Stage 3a) ──────────────
#
# ★ Stage 2의 ESO IRSA(irsa-eso.tf)와 **구조는 같고 의미가 다르다.** 그 차이가 이번 학습 포인트다.
#
#   ESO      : 파드가 **AWS API에서 값을 읽는다**(Secrets Manager). 실패해도 앱만 못 뜬다.
#   EBS CSI  : 파드가 **AWS 리소스를 만들고 지운다**(CreateVolume/DeleteVolume/Attach…).
#              즉 클러스터 안의 컨트롤러에게 **인프라를 생성할 권한**을 주는 것이다.
#              → PVC 하나 만들면 K8s가 알아서 EBS를 만든다 = "tofu state 밖의 AWS 리소스"가
#                생기는 지점이고, Stage 1에서 배운 **고아 과금**의 발원지가 바로 여기다.
#                (그래서 SOP §8이 destroy 전 `kubectl delete pvc --all -A`를 요구한다.)
#
# 왜 노드 역할에 정책을 붙이지 않나:
#   붙이면 동작은 한다(그리고 그게 흔한 지름길이다). 하지만 그 순간 **같은 노드의 모든 파드가
#   EBS를 만들고 지울 수 있게 된다.** irsa-eso.tf에서 설명한 "권한 경계가 노드라서 최소권한이
#   성립하지 않는" 문제가, 읽기가 아니라 **쓰기 권한**으로 재현되는 것이라 훨씬 나쁘다.
#
# ⚠️ `aws eks describe-addon-versions --addon-name aws-ebs-csi-driver` 실측:
#     requiresIamPermissions: True  ← 애드온만 추가하면 PVC가 영원히 Pending이다.

locals {
  # 애드온이 만드는 ServiceAccount 이름은 **AWS가 정한다**(우리가 못 바꾼다).
  # 이 값이 틀리면 sub 조건이 안 맞아 AssumeRoleWithWebIdentity가 거부된다 —
  # Stage 2에서 겪은 IRSA 최다 실패 모드와 똑같은 증상이 난다.
  ebs_csi_namespace       = "kube-system"
  ebs_csi_service_account = "ebs-csi-controller-sa"
}

data "aws_iam_policy_document" "ebs_csi_trust" {
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
      values   = ["system:serviceaccount:${local.ebs_csi_namespace}:${local.ebs_csi_service_account}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# 🔴 IAM description은 ASCII만 (07-28 apply 실측 — irsa-eso.tf:61 주석 참조)
# 의미: EBS CSI 드라이버 컨트롤러가 EBS 볼륨을 생성/연결/삭제하기 위한 IRSA 역할
resource "aws_iam_role" "ebs_csi" {
  name               = "${var.cluster_name}-ebs-csi"
  description        = "IRSA role for EBS CSI driver controller to manage EBS volumes"
  assume_role_policy = data.aws_iam_policy_document.ebs_csi_trust.json
}

# ── 왜 여기선 직접 만들지 않고 AWS 관리형 정책을 붙이나 ──
# ESO 때는 시크릿 3개로 리소스를 좁힌 인라인 정책을 직접 썼다. 여기서 같은 걸 하려면
# "아직 만들어지지 않은 볼륨"의 ARN을 미리 알아야 하는데 그게 불가능하다
# (동적 프로비저닝의 본질 — 이름을 CSI가 런타임에 정한다).
# AmazonEBSCSIDriverPolicy는 그 대신 **조건 키로** 범위를 좁힌다:
#   Create/Delete는 `aws:RequestTag/ebs.csi.aws.com/cluster = true` 태그가 붙은 볼륨에만 허용.
#   즉 "CSI가 만든 볼륨만 CSI가 지울 수 있다" — 리소스가 아니라 **출처**로 경계를 긋는 방식.
# → 최소권한을 리소스 ARN으로 못 그을 때 태그 조건으로 긋는 실례. 직접 쓰는 것보다 안전하다.
resource "aws_iam_role_policy_attachment" "ebs_csi" {
  role       = aws_iam_role.ebs_csi.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}
