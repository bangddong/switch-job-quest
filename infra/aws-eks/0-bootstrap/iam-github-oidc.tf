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

# 2) PR plan 용 역할. 신뢰정책 —
#    이 레포의 main 브랜치 + pull_request(plan)에서 온 토큰만 허용. (sub 에 main 이 남아 있어도 쓰기 권한이
#    없으므로 main 에서 assume 해도 읽기뿐이다. 굳이 빼지 않은 것은 변경 최소화 — sub 변경은 in-place,
#    역할 **이름** 변경만 replace 다. QA F-4)
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

# 3) 역할 권한 = **좁힌 읽기 전용** — PR 의 `tofu plan` 전용 (📌D-016 순서표 3-B, 2026-10-19).
#    ~~AdministratorAccess (학습 전용계정 근거)~~ → prod 가 이 계정에 들어오면서 근거가 거짓이 됐다(N-18).
#    PR 은 머지 권한 없이 워크플로를 고쳐 실행할 수 있으므로, 여기 붙는 권한 = **PR 이 가진 권한**이다.
#
#    구성 = 관리형 ReadOnlyAccess(plan 의 refresh 가 부르는 Describe/Get/List) + 아래 인라인 정책:
#      Allow 락 테이블 쓰기 — `plan` 도 DynamoDB 락을 잡는다(PutItem/DeleteItem). 없으면 plan 이 실패(B3-4)
#      Deny  state 객체 읽기는 `0-bootstrap/*`·`1-network/*` 만 — 2-cluster state(Secrets Manager 값·TLS
#            개인키)와 백업 버킷(pg_dump = prod 데이터)을 막는다. ReadOnlyAccess 는 **전 버킷** GetObject 다(B3-5)
#      Allow KMS — DynamoDB 경유만(락 테이블 SSE). Deny 그 외 경로의 kms:Decrypt
#      Deny  시크릿 값·파라미터·로그 본문 — ReadOnlyAccess 가 열어 두는 "내용" 읽기
#      Deny  락 테이블 밖 DynamoDB 아이템 읽기 — 미래 테이블 대비
#    ⚠️ ReadOnlyAccess + 거부 목록은 **열린 구조**다 — 목록 밖 내용 읽기(예: ECR 이미지 pull,
#       `cloudtrail:LookupEvents`)는 남는다. 위협 주체 = 브랜치를 만들 수 있는 사람(1명)이라 감수(QA F-2).
#    🔴 이 역할을 EKS access entry 에 넣지 마라 — ReadOnlyAccess 는 `eks:AccessKubernetesApi` 를 포함할 수
#       있다. 3-C 에서 entry 는 **main 역할만**.
#    🔴 **남는 노출 (감수 — 사용자 결정 2026-10-19)**: 0-bootstrap state 에는 `random_password` 평문
#       (JWT prod 키·postgres 비밀번호)이 있고 plan 은 그걸 읽어야 한다. 노출 대상 = 이 레포에 브랜치를
#       만들 수 있는 사람(쓰기 권한자 1명 실측). 포크 PR 은 OIDC 토큰을 받지 못한다.
#    ⚠️ Deny 는 Allow 를 이긴다 — 여기 막힌 동작이 plan 에 필요해지면 plan 이 AccessDenied 로 죽는다.
#       0-bootstrap·1-network 에 Secrets Manager **값**(version)을 두면 그렇게 된다(B3-6 → 5번 설계 제약).
resource "aws_iam_role_policy_attachment" "github_actions_readonly" {
  role       = aws_iam_role.github_actions.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

#tfsec:ignore:aws-iam-no-policy-wildcards — `Resource = "*"` 는 **Deny** 문에만 쓴다(넓게 막는 쪽)
resource "aws_iam_role_policy" "github_actions_plan_scope" {
  name = "tofu-plan-scope"
  role = aws_iam_role.github_actions.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # 락 행은 이 두 레이어 것만 (QA F-3). 테이블 전체면 PR 이 진행 중인 main apply 의 락을 지우거나
        # `-md5` digest 행을 오염시켜 apply 를 막을 수 있다. LockID = "<버킷>/<레이어>/terraform.tfstate[-md5]".
        Sid      = "StateLock"
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:DeleteItem"]
        Resource = aws_dynamodb_table.tflock.arn
        Condition = {
          "ForAllValues:StringLike" = {
            "dynamodb:LeadingKeys" = [
              "${var.state_bucket_name}/0-bootstrap/*",
              "${var.state_bucket_name}/1-network/*",
            ]
          }
        }
      },
      {
        # 락 테이블은 SSE(KMS `aws/dynamodb`)라 Get/PutItem 이 **호출자 신원으로** kms 를 부른다.
        # ReadOnlyAccess 엔 kms:Decrypt 가 없다 → 이게 없으면 plan 이 락 단계에서 죽는다(QA F-1, 실측 2026-10-19:
        # `Error acquiring the state lock … not authorized to perform: kms:Decrypt`). DynamoDB 경유만 연다.
        Sid      = "StateLockKms"
        Effect   = "Allow"
        Action   = ["kms:Decrypt", "kms:Encrypt", "kms:GenerateDataKey*", "kms:DescribeKey"]
        Resource = "*"
        Condition = {
          StringEquals = { "kms:ViaService" = "dynamodb.${var.region}.amazonaws.com" }
        }
      },
      {
        Sid    = "DenyObjectReadOutsidePlanState"
        Effect = "Deny"
        Action = ["s3:GetObject", "s3:GetObjectVersion"]
        NotResource = [
          "${aws_s3_bucket.tfstate.arn}/0-bootstrap/*",
          "${aws_s3_bucket.tfstate.arn}/1-network/*",
        ]
      },
      {
        Sid    = "DenyContentReads"
        Effect = "Deny"
        Action = [
          "secretsmanager:GetSecretValue",
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath",
          "ssm:GetParameterHistory", # String 타입은 평문 반환 (QA F-2)
          "dynamodb:GetRecords",     # 스트림 본문 (QA F-2)
          "ec2:GetConsoleOutput",    # 인스턴스 콘솔 (QA F-2)
          "logs:GetLogEvents",
          "logs:FilterLogEvents",
          "logs:StartQuery",
          "logs:GetQueryResults",
          "logs:StartLiveTail",
        ]
        Resource = "*"
      },
      {
        # kms:Decrypt 는 위 StateLockKms(DynamoDB 경유)만 — 그 외 경로의 복호화는 막는다.
        Sid      = "DenyKmsDecryptOutsideDynamo"
        Effect   = "Deny"
        Action   = "kms:Decrypt"
        Resource = "*"
        Condition = {
          StringNotEquals = { "kms:ViaService" = "dynamodb.${var.region}.amazonaws.com" }
        }
      },
      {
        Sid         = "DenyTableReadOutsideLock"
        Effect      = "Deny"
        Action      = ["dynamodb:GetItem", "dynamodb:BatchGetItem", "dynamodb:Query", "dynamodb:Scan"]
        NotResource = aws_dynamodb_table.tflock.arn
      },
    ]
  })
}

# ══ 📌D-016 순서표 3 — main 전용 쓰기 역할 (2026-10-19) ══════════════════════
#
# 왜: EKS 가 평일 창 동안 prod 를 서빙하면 CI 가 클러스터를 켜고 끈다(순서표 8).
#   위 역할은 신뢰정책에 `pull_request` 가 있고 Admin 이라 **PR 코드가 리뷰 전에 Admin 으로
#   돈다**(PR 이 워크플로 파일을 고치면 그대로 실행된다). 학습 계정이라 감수했던 위험이다(N-2·N-18).
#   ⇒ 쓰기는 **main 에 들어간 워크플로만** 하도록 역할을 둘로 나눈다.
#   ⚠️ "main 에 들어감" ≠ "사람 리뷰" (QA F-1, 실측 2026-10-19): main 보호는 필수 체크 6개뿐이고
#      `required_pull_request_reviews: null`·`enforce_admins: false`. 실제 경계 = **머지 권한(본인 1명) +
#      필수 체크 통과**. 1인 레포라 리뷰 강제는 걸 수 없다(자기 PR 승인 불가). 목적은 *머지 권한 없는
#      PR 코드가 Admin 으로 도는 것* 을 막는 것이고, 그건 이걸로 달성된다.
#
#   | 역할 | sub | 권한 | 쓰는 곳 |
#   |---|---|---|---|
#   | 위 `github_actions` | main + pull_request | **좁힌 읽기 전용** | infra-deploy PR plan |
#   | 이것 `github_actions_main` | **main 만** | Admin | infra-deploy apply · ecr-push · 전환(8) |
#
# 🔴 **이 역할의 보호 효과는 위 역할이 좁혀진 뒤에야 생긴다** (Blindspot B3-1). 위 역할이 Admin 이면
#    PR 이 `eks:CreateAccessEntry` 로 자기를 클러스터 admin 으로 넣거나 `iam:UpdateAssumeRolePolicy` 로
#    이 역할의 신뢰정책을 고칠 수 있다. 위 역할을 다시 넓히지 마라 — 그 순간 이 분리가 무의미해진다.
#    순서(자기 자격증명을 고치는 문제 — B3-2): ① 이 역할 생성(3-A #464) → 시크릿 `AWS_ROLE_ARN_MAIN`
#    등록 → ② 워크플로 전환 + 위 역할 축소(3-B, 머지 apply 는 이 역할이 수행).
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
#   `workflow_dispatch` 로 돌아도 같은 sub 다(B3-9). 경계 = **main 의 워크플로 정의 + 머지 권한 + 필수 체크**.
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
