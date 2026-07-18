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
