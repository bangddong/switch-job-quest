# ── DB 백업 버킷 (선행 조건 1: 백업·복구 리허설) ────────────────────────────
#
# 왜 0-bootstrap 인가 — **이 한 줄이 이 파일의 전부다.**
#
#   백업은 자기가 백업하는 대상보다 오래 살아야 한다.
#
# 데이터 볼륨(`ebs-postgres.tf`)이 이 레이어에 있으므로 백업도 최소 이 레이어여야 한다.
# 2-cluster 에 두면 세션마다 destroy 와 함께 사라지고, 같은 EBS 에 두면 애초에 백업이 아니다.
# D-004·L-14 가 확립한 규칙 — *"수명이 같아야 하는 것은 같은 레이어에 둔다"* — 의 세 번째 적용이다
# (①postgres 비밀번호 L-14 ②영속 EBS D-004 ③백업, 여기).
#
# ⚠️ **이 리소스는 기록된 결정을 뒤집는다.** `PERSISTENT-RESOURCES.md` 의 EBS 절이
#    *"스냅샷 백업은 **의도적으로 만들지 않는다** — 데이터가 Flyway 마이그레이션 12개로 전부
#    재생성 가능"* 이라고 적어 뒀다. 그 판단은 **학습장 전제에서는 여전히 옳고**, 뒤집는 이유는
#    목적이 바뀌었기 때문이다: 지금 필요한 것은 *데이터 보존*이 아니라 **복구 절차의 리허설**이다.
#    (prod 이관 후에는 전제 자체가 사라진다 — 사용자 데이터는 마이그레이션으로 재생성되지 않는다.)
#    재판정 표시는 원장 쪽 원본 블록에 있다(`design-change-procedure.md` §4).
#
# 비용: 덤프가 KB 단위(26행 DB)라 월 ~$0. 그래도 **상한 없는 영속 리소스는 조용히 자라는 것이
#      기본값**이므로 아래 lifecycle 로 상한을 못 박는다(원장 §규칙).

# tfsec 판단: aws-s3-enable-bucket-logging — 접근 로깅은 별도 로그 버킷이 필요하다.
#   1인 학습장에서 백업 버킷 하나를 감시하려고 버킷을 하나 더 만드는 것은 과투자다.
#   `backend-state.tf:9` 가 tfstate 버킷에 대해 같은 근거로 같은 판단을 이미 했다.
#   ⚠️ prod 이관 후에는 재판단 대상이다 — 그때는 "누가 백업을 읽어갔나"가 실제 감사 항목이 된다.
#tfsec:ignore:aws-s3-enable-bucket-logging
resource "aws_s3_bucket" "db_backups" {
  bucket = var.backup_bucket_name

  # force_destroy 를 켜지 않는다. 버저닝이 켜진 버킷은 객체가 남아 있으면 DeleteBucket 이
  # BucketNotEmpty 로 실패한다 — 즉 **bare `tofu destroy` 오타에 대한 latch 로도 동작한다**
  # (`backend-state.tf` 의 tfstate 버킷과 같은 구조). 정말 지우려면 원장의 제거 절차를 따른다.

  tags = {
    Name = "devquest-eks-db-backups"
    # 🔴 **S3 태그 값 규칙은 EC2 보다 엄격하다** (2026-09-13 실측, apply 가 여기서 죽었다):
    #      api error InvalidTag: The TagValue you have provided is invalid
    #    EBS 볼륨(`ebs-postgres.tf`)에는 같은 형태 — 한글 + em-dash — 가 들어가 있고 통과한다.
    #    S3 가 허용하는 것은 letters/numbers/spaces 와 `+ - = . _ : / @` 뿐이라
    #    **괄호와 em-dash 가 걸린다.** 같은 계정, 같은 terraform, 다른 서비스, 다른 규칙이다.
    #    → 태그 값은 ASCII 로 쓴다. 설명은 이 주석이 담당한다(태그는 검색·과금 배분용이지
    #      문서가 아니다).
    Purpose = "pg_dump logical backups for in-cluster Postgres"

    # 🔑 EBS 와 같은 축. 이 버킷은 세션과 함께 사라지지 않는 것이 **정상**이다.
    #    (S3 는 SOP §9 고아 검사 대상이 아니지만, 원장 §확인 명령에는 넣는다 — 안 넣으면
    #     신설 버킷이 원장 대조에서 영원히 안 보인다.)
    Persistent = "true"
    ReviewBy   = "2027-01-15"
  }
}

# 버저닝 — 백업 버킷에서는 "실수로 덮어쓴 백업"이 가장 흔한 사고다.
# 같은 키에 두 번 올리면 이전 덤프가 사라지는데, 하필 그 시점이 복구가 필요한 시점이다.
resource "aws_s3_bucket_versioning" "db_backups" {
  bucket = aws_s3_bucket.db_backups.id
  versioning_configuration {
    status = "Enabled"
  }
}

# ── 🔒 증가 상한 — 원장 규칙 "상한 칸이 비어 있으면 등재 완료 금지" ──────────
#
# 🔴 **버저닝을 켜면 `expiration` 만으로는 상한이 아니다.** 현행 객체가 만료돼도
#    noncurrent 버전은 남는다. 세 규칙이 다 있어야 상한이 성립한다:
#
#   ① expiration                        현행 버전의 수명
#   ② noncurrent_version_expiration     덮어쓰기로 밀려난 버전의 수명  ← 빼먹기 쉽다
#   ③ abort_incomplete_multipart_upload 중단된 업로드 조각            ← 영원히 남고 조회도 안 된다
#
# ③이 특히 고약하다 — `aws s3 ls` 에 안 보이는데 과금은 된다. 덤프가 KB 단위라
# multipart 로 갈 일이 없지만, 볼륨이 커진 뒤 큰 덤프를 올리다 Ctrl-C 하면 바로 발생한다.
resource "aws_s3_bucket_lifecycle_configuration" "db_backups" {
  bucket = aws_s3_bucket.db_backups.id

  rule {
    id     = "expire-backups"
    status = "Enabled"

    filter {} # 버킷 전체

    expiration {
      days = var.backup_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = var.backup_retention_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# tfsec 판단: aws-s3-encryption-customer-key — AES256(SSE-S3, $0)로 암호화 유지, CMK 는 월 ~$1.
#   이 버킷이 담는 것은 **학습장 DB 의 논리 덤프**이고 그 데이터는 마이그레이션으로 재생성된다.
#   자물쇠가 지키려는 데이터보다 비싸지는 구조 — `ebs-postgres.tf:16-23` 과 동일한 판단이다.
#   🔴 **prod 이관 시 이 판단은 뒤집힌다.** 그때 덤프에는 사용자 데이터가 들어간다.
#tfsec:ignore:aws-s3-encryption-customer-key
resource "aws_s3_bucket_server_side_encryption_configuration" "db_backups" {
  bucket = aws_s3_bucket.db_backups.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 🔴 백업 버킷이 퍼블릭이 되는 것은 tfstate 가 퍼블릭이 되는 것과 같은 급의 사고다.
#    덤프에는 스키마 전체와 시드 데이터가 들어 있다.
resource "aws_s3_bucket_public_access_block" "db_backups" {
  bucket                  = aws_s3_bucket.db_backups.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
