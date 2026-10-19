# GitHub Actions ↔ AWS 인증을 OIDC 페더레이션으로 — 장기 액세스키 저장 없음.
#
# 흐름: GitHub Actions 실행 → GitHub이 단명 OIDC 토큰 발급 → AWS STS가
#   AssumeRoleWithWebIdentity로 임시 자격증명 교환. 신뢰의 뿌리 = 아래 provider,
#   "누가 쓸 수 있나" = role의 assume_role_policy(this 레포·특정 참조만).

# GitHub OIDC 발급자의 TLS 인증서 → 지문(thumbprint)을 코드로 동적 확보.
# (AWS는 2023년 이후 잘 알려진 IdP의 지문 검증을 사실상 생략하지만 필드는 여전히 필수)
data "tls_certificate" "github" {
  url = "https://token.actions.githubusercontent.com"
}

# 1) GitHub을 신뢰할 신원 공급자로 AWS에 등록
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"] # 토큰 audience — AWS STS
  thumbprint_list = [data.tls_certificate.github.certificates[0].sha1_fingerprint]
}

# 2) CI가 assume할 역할. 신뢰정책이 핵심 보안 경계 —
#    이 레포의 main 브랜치(merge) + pull_request(plan)에서 온 토큰만 허용.
#    sub 조건이 없으면 임의의 GitHub 레포가 이 역할을 훔쳐 쓸 수 있다.
resource "aws_iam_role" "github_actions" {
  name = "devquest-eks-github-actions"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        StringLike = {
          "token.actions.githubusercontent.com:sub" = [
            "repo:${var.github_repo}:ref:refs/heads/main",
            "repo:${var.github_repo}:pull_request",
          ]
        }
      }
    }]
  })
}

# 3) 역할 권한 = AdministratorAccess.
#    사용자 결정(학습 전용계정, prod는 Fly 별도): 권한은 넓게 두되 위 신뢰정책으로 탈취를 막는다.
#    prod 계정이었다면 최소권한 스코프 정책을 썼을 것.
#tfsec:ignore:aws-iam-no-policy-wildcards
resource "aws_iam_role_policy_attachment" "github_actions_admin" {
  role       = aws_iam_role.github_actions.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}

# ══ 📌D-016 순서표 3 — main 전용 쓰기 역할 (2026-10-19) ══════════════════════
#
# 왜: EKS 가 평일 창 동안 prod 를 서빙하면 CI 가 클러스터를 켜고 끈다(순서표 8).
#   위 역할은 신뢰정책에 `pull_request` 가 있고 Admin 이라 **PR 코드가 리뷰 전에 Admin 으로
#   돈다**(PR 이 워크플로 파일을 고치면 그대로 실행된다). 학습 계정이라 감수했던 위험이다(N-2·N-18).
#   ⇒ 쓰기는 **main 에 들어간(= 리뷰를 거친) 워크플로만** 하도록 역할을 둘로 나눈다.
#
#   | 역할 | sub | 권한 | 쓰는 곳 |
#   |---|---|---|---|
#   | 위 `github_actions` | main + pull_request | (다음 PR 에서) **좁힌 읽기 전용** | infra-deploy PR plan |
#   | 이것 `github_actions_main` | **main 만** | Admin | infra-deploy apply · ecr-push · 전환(8) |
#
# 🔴 **이 역할만으로는 보호 효과가 0이다** (Blindspot B3-1). 위 역할이 Admin 인 동안은 PR 이
#    `eks:CreateAccessEntry` 로 자기를 클러스터 admin 으로 넣거나 `iam:UpdateAssumeRolePolicy` 로
#    이 역할의 신뢰정책을 고칠 수 있다. **위 역할 축소가 끝나야** 경계가 선다.
#    순서(자기 자격증명을 고치는 문제 — B3-2): ① 이 역할 생성(이 PR) → 시크릿 `AWS_ROLE_ARN_MAIN`
#    등록 → ② 워크플로 전환 + 위 역할 축소(다음 PR, 머지 apply 는 이 역할이 수행).
#
# Admin 인 이유 (B3-11): 0-bootstrap apply 주체는 IAM 역할·OIDC provider·**자기 신뢰정책**을
#   관리하므로 `iam:*` 가 필요하고, 그건 권한 상승이 가능한 admin 과 같다. 최소권한으로 좁혀도
#   경계는 결국 이 신뢰정책 하나다. 그리고 EKS apply 권한을 최소로 맞추다 **유료 세션 도중 권한
#   부족으로 apply 가 멈추는** 비용이 더 크다. 이 역할이 자기 신뢰정책을 깨뜨리면 CI 는 스스로
#   복구할 수 없다 → 복구 경로는 로컬 `bootstrap-admin` 뿐(B3-8).
#
# sub 를 `StringEquals` 로 정확히 맞춘다(와일드카드 없음). ⚠️ 워크플로에 `environment:` 를 붙이면
#   sub 가 `repo:<repo>:environment:<name>` 으로 **바뀌어 assume 이 실패한다**(B3-10). 쓰지 않는다.
# ⚠️ `ref:refs/heads/main` 은 "머지 apply 전용"이 아니다 — main 의 워크플로가 `schedule`·
#   `workflow_dispatch` 로 돌아도 같은 sub 다(B3-9). 경계 = **main 의 워크플로 정의 + 브랜치 보호**.
resource "aws_iam_role" "github_actions_main" {
  name = "devquest-eks-github-actions-main"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:ref:refs/heads/main"
        }
      }
    }]
  })
}

#tfsec:ignore:aws-iam-no-policy-wildcards
resource "aws_iam_role_policy_attachment" "github_actions_main_admin" {
  role       = aws_iam_role.github_actions_main.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}
