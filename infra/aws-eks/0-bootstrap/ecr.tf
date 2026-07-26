# ── ECR (Elastic Container Registry) — 앱 컨테이너 이미지 저장소 ──────────────
#
# 왜 0-bootstrap(영속 레이어)에 두나:
#   ECR을 2-cluster(destroy 대상)에 두면 클러스터를 부술 때마다 이미지가 전멸한다 → 매 세션
#   Spring Boot 이미지 재빌드·재푸시(5~10분×N서비스). destroy-after-use 규율의 실질 마찰이 된다.
#   이미지는 클러스터 수명과 무관한 영속 자산이므로 계정 수준 공유 레이어(0-bootstrap)에 둔다.
#   (README의 "ECR = 2-cluster 소속" 기술은 이 결정으로 폐기 — 2026-07-26. CONTEXT "ECR 구멍" 참조.)
#
# 비용: 저장만 과금($0.10/GB-Mo). 빈 레포는 $0. 아래 lifecycle policy로 무한 누적을 막는다.
#   node가 이미지를 pull하는 권한은 이미 nodes.tf의 AmazonEC2ContainerRegistryReadOnly로 부여됨.

resource "aws_ecr_repository" "app" {
  for_each = toset(var.ecr_repositories)

  name = "devquest/${each.value}"

  # 학습 편의: 같은 태그(latest 등) 재푸시 허용. prod 전환 시 IMMUTABLE 권장(태그 덮어쓰기 방지).
  image_tag_mutability = var.ecr_image_tag_mutability

  # 푸시 시 기본 취약점 스캔(무료). 결과는 콘솔/`aws ecr describe-image-scan-findings`로 확인.
  image_scanning_configuration {
    scan_on_push = true
  }

  # 기본 AES256 암호화(무료). KMS는 월 $1+ 라 학습장에선 생략(prod 전환 체크리스트 대상).
  encryption_configuration {
    encryption_type = "AES256"
  }
}

# 무한 누적 방지 — 이게 ECR을 "영속으로 둬도 안전"하게 만드는 전제다.
#   규칙은 rulePriority 오름차순으로 평가된다. tagStatus="any"는 반드시 마지막(최고 우선순위 번호)에.
resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "태그 없는(빌드 중간산물) 이미지는 1일 후 만료"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "최근 ${var.ecr_keep_last_images}개 이미지만 유지, 초과분 만료"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_keep_last_images
        }
        action = { type = "expire" }
      }
    ]
  })
}
