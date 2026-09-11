# ── ⑪ IRSA — AWS Load Balancer Controller용 파드 IAM 역할 (Stage 4) ───
#
# LBC(AWS Load Balancer Controller)가 하는 일: 클러스터의 `Ingress`·`Service(LoadBalancer)`
# 오브젝트를 감시하다가 **실제 AWS ALB/NLB를 만들고 지운다.** 즉 K8s 선언을 AWS 리소스로
# 번역하는 컨트롤러다. 그래서 이 파드는 elasticloadbalancing·ec2 API를 호출할 권한이 필요하고,
# 그 권한을 노드가 아니라 **ServiceAccount에 묶는** 것이 IRSA다(원리는 irsa-eso.tf ⑩ 참조).
#
# 🔴 **역할만 여기서 만든다. 컨트롤러 설치는 tofu가 하지 않는다.**
#   이 레이어의 provider는 aws·tls·random 뿐이고(versions.tf) helm/kubernetes provider가 없다.
#   레포 관례는 **"AWS 1st-party 애드온만 `aws_eks_addon`, 서드파티는 CLI helm"** 이다 —
#   ESO도 그렇게 깔았다(`docs/eks-tutorial-steps.md` 2-2).
#   ⚠️ LBC는 EKS 애드온으로 **제공되지 않는다.** 2026-09-11 실측:
#     `aws eks describe-addon-versions --addon-name aws-load-balancer-controller` → `None`
#   착수 전에 이걸 확인한 이유: 애드온이 있으면 `addons.tf`에 한 줄로 끝났을 것이고,
#   그 확인은 **무료**인데 안 하면 과금 중에 알게 된다.

locals {
  # helm install 시 지정하는 값과 **정확히 일치해야** 신뢰정책의 sub 조건이 맞는다.
  # 불일치 증상: "Not authorized to perform sts:AssumeRoleWithWebIdentity" (irsa-eso.tf ⑩ 참조)
  #
  # ⚠️ ESO와 다른 점: LBC 차트는 **ServiceAccount를 스스로 만든다.** 그래서 helm 인자가
  #   `--set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"=<ARN>` 형태이고,
  #   annotation 키의 **점을 이스케이프**해야 한다(helm이 점을 경로 구분자로 읽는다).
  #   이스케이프를 빠뜨리면 helm이 `eks → amazonaws → com/role-arn` 중첩 맵을 만들어
  #   **에러 없이** 엉뚱한 구조를 넣는다 — annotation이 안 붙은 채로 컨트롤러가 뜬다.
  alb_namespace       = "kube-system"
  alb_service_account = "aws-load-balancer-controller"
}

data "aws_iam_policy_document" "alb_trust" {
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
      values   = ["system:serviceaccount:${local.alb_namespace}:${local.alb_service_account}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# 의미: AWS Load Balancer Controller가 ALB/NLB를 관리하기 위한 IRSA 역할
# (description은 ASCII만 — 한글은 ValidationError. irsa-eso.tf의 실측 주석 참조)
resource "aws_iam_role" "alb_controller" {
  name               = "${var.cluster_name}-alb-controller"
  description        = "IRSA role for AWS Load Balancer Controller"
  assume_role_policy = data.aws_iam_policy_document.alb_trust.json
}

# ── 정책: 공식 JSON을 그대로 쓴다 ──────────────────────────────────
#
# 출처: kubernetes-sigs/aws-load-balancer-controller **v3.5.0**
#       docs/install/iam_policy.json
#       sha256 16f232c9d9f79366fe949c4550ad517a202380058a9e48d45a4e215044a20a6a
#       (차트 버전과 맞춘다 — `helm search repo eks/aws-load-balancer-controller --versions`)
#
# 🔴 **`jsonencode(jsondecode(...))`는 멋부림이 아니라 필수다 (2026-09-11 착수 전 발견).**
#   customer managed policy의 크기 상한은 **6,144자**인데 공식 파일은 들여쓰기 포함 **8,955자**다.
#   `policy = file(...)`로 원본을 그대로 넘기면 상한을 넘겨 **apply가 실패한다.**
#   decode→encode 왕복이 공백을 걷어내 **5,196자**가 된다(여유 948자).
#   ```
#   원본 8955 → minify 5196 / 상한 6144
#   ```
#   ⚠️ 여유가 크지 않다. 상위 버전에서 statement가 늘면 다시 넘을 수 있다 —
#      버전을 올릴 때 **크기부터 재라**. 넘으면 정책을 둘로 쪼개 각각 첨부해야 한다.
#
# 🔴 **와일드카드가 10개 statement에 있다. 줄이지 않는다.**
#   LBC는 자기가 **앞으로 만들** ALB·타겟그룹·보안그룹을 미리 알 수 없다. 리소스 ARN이
#   런타임에 생기므로 `Resource: "*"` + 조건 키(`aws:ResourceTag/elbv2.k8s.aws/cluster` 등)로
#   좁히는 것이 공식 설계다. 임의로 깎으면 **과금 중에** AccessDenied를 만난다.
#
# 🔴 **tfsec가 이 파일을 검사하지 못한다 — 구멍이 있다는 사실을 여기 적어둔다.**
#   `aws-iam-no-policy-wildcards`는 HCL 안의 정책 문서를 본다. 외부 JSON을 `file()`로
#   읽으면 검사 대상 밖이라 **조용히 통과한다.** 이건 우회가 아니라 도구의 한계지만,
#   결과적으로 *"CI가 통과했으니 와일드카드가 없다"* 는 추론은 **여기서 성립하지 않는다.**
#   ⚠️ 이 주석을 지우지 마라. 이 레포가 반복해서 데인 실패가 정확히
#      **"검사가 주장보다 헐거운데 아무도 그 사실을 모르는"** 형태다.
#   대안으로 정책을 HCL `data` 블록에 풀어쓰고 `#tfsec:ignore`를 다는 방법이 있는데,
#   16개 statement를 손으로 옮기면 **공식본과 갈라질 위험**이 생긴다. 갈라짐은 조용하고
#   런타임에만 드러나므로, 여기서는 **원본 보존 + 구멍 명시**를 택했다. sha256을 박아둔 것도
#   그래서다 — 파일이 바뀌면 이 주석과 대조해 알아챌 수 있다.
resource "aws_iam_policy" "alb_controller" {
  name = "${var.cluster_name}-alb-controller"
  # 의미: LBC — ALB/NLB 및 관련 EC2 네트워킹 리소스 관리
  description = "AWS Load Balancer Controller policy (official v3.5.0)"
  policy      = jsonencode(jsondecode(file("${path.module}/iam-policy-alb-controller.json")))
}

resource "aws_iam_role_policy_attachment" "alb_controller" {
  role       = aws_iam_role.alb_controller.name
  policy_arn = aws_iam_policy.alb_controller.arn
}
