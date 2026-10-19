# ── ⑦ Access Entry — 누가 클러스터 안에서 무엇을 할 수 있나 ──────
#
# `cluster.tf` 가 `authentication_mode = "API"` · `bootstrap_cluster_creator_admin_permissions = false`
# 라서 **여기 entry 가 없으면 kubectl 은 전부 거부된다.** 만든 사람도 자동 admin 이 아니다.
#
# 📌D-016 순서표 3-C (2026-10-19) — entry 를 **명시 ARN 2개**로 바꿨다.
#   예전엔 `principal_arn = data.aws_caller_identity.current.arn`(= apply 하는 주체) 1개였다.
#   CI 가 apply 하면 그 값이 `arn:aws:sts::…:assumed-role/<역할>/<세션>` **STS 세션 ARN** 이 되어
#   access entry 를 만들 수 없고(N-1), 역할 ARN 으로 바꾸면 로컬 admin 이 kubectl 권한을 잃는다.
#   ⇒ 누가 apply 하든 같은 두 주체가 들어가게 한다.
#
#   | entry | 주체 | 왜 |
#   |---|---|---|
#   | `admin` | IAM 사용자 `bootstrap-admin`(로컬) | 사람의 kubectl · CI 가 망가졌을 때의 복구 경로 |
#   | `ci_main` | 역할 `devquest-eks-github-actions-main` | 전환 워크플로(순서표 8)가 Ingress 선삭제·ArgoCD 조작 |
#
# 🔴 **PR plan 용 역할(`devquest-eks-github-actions`)은 넣지 마라.** ReadOnlyAccess 를 갖고 있고 PR 코드가
#    머지 전에 그 역할로 돈다. entry 를 주면 *PR 로 prod 클러스터에 접근* 이 다시 열린다(N-2).
#    main 역할도 PR 역할이 좁혀진 **뒤에야** 의미가 있었다(3-B #465, Blindspot B3-1).
#
# ⚠️ `bootstrap-admin` 은 장기 액세스키 사용자다. 키를 폐기(일지의 *"OIDC 전환 후 폐기 예정"*)할 때
#    이 entry 를 다른 사람 주체로 바꿔야 한다 — 안 그러면 사람이 들어갈 문이 없다(B3-8).
#
# 계정 ID 는 커밋하지 않는다 — `data.aws_caller_identity` 로 조립한다(퍼블릭 레포).

# 🔴 지우지 마라 — `irsa-fluent-bit.tf`(로그 그룹 ARN)·`irsa-karpenter.tf`(account_id)가 같이 쓴다(B3-7).
data "aws_caller_identity" "current" {}

locals {
  local_admin_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/${var.local_admin_iam_user}"
  # 0-bootstrap 이 단일 출처 — 여기서 이름으로 다시 조립하지 않는다.
  ci_main_role_arn = data.terraform_remote_state.bootstrap.outputs.github_actions_main_role_arn
}

# 리소스 주소(`admin`)는 그대로 둔다 — `for_each` 로 바꾸면 state 주소가 바뀌어 세션 중 apply 시
# 로컬 admin entry 가 지워졌다 다시 생긴다(그 사이 kubectl 거부, B3-17).
resource "aws_eks_access_entry" "admin" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = local.local_admin_arn
  type          = "STANDARD"
}

resource "aws_eks_access_policy_association" "admin" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = local.local_admin_arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.admin]
}

resource "aws_eks_access_entry" "ci_main" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = local.ci_main_role_arn
  type          = "STANDARD"
}

resource "aws_eks_access_policy_association" "ci_main" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = local.ci_main_role_arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.ci_main]
}
