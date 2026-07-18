# 이 스택이 만드는 S3 버킷 + DynamoDB 락 테이블 = 모든 레이어(및 자기 자신)의 remote backend.
#
# 부트스트랩 닭-달걀:
#   1) backend 블록 없이(=로컬 state) `tofu apply` → 아래 버킷·테이블 생성
#   2) backend.tf 활성화 후 `tofu init -migrate-state` → 로컬 state를 S3로 이관
#
# ⚠️ public repo: state 파일에는 평문 secret이 들어갈 수 있음 → *.tfstate는 절대 커밋 금지(.gitignore).

# tfsec 판단: aws-s3-enable-bucket-logging — 접근 로깅은 별도 로그 버킷 필요 → 1인 학습 tfstate엔 과함.
#tfsec:ignore:aws-s3-enable-bucket-logging
resource "aws_s3_bucket" "tfstate" {
  bucket = var.state_bucket_name
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  versioning_configuration {
    status = "Enabled"
  }
}

# tfsec 판단: aws-s3-encryption-customer-key — AES256(SSE-S3)로 암호화 유지, CMK는 월 ~$1 과투자.
#   tfstate는 비공개·버전관리·암호화된 버킷에 있어 학습장엔 CMK 감사·회전 이득이 미미.
#tfsec:ignore:aws-s3-encryption-customer-key
resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket                  = aws_s3_bucket.tfstate.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# tfsec 판단 기록:
#   enable-recovery(PITR): 락 테이블은 잠깐 lock 항목만 들었다 나감 = 복구할 데이터 없음 → 무시.
#   table-customer-key(CMK): 아래 server_side_encryption은 AWS 관리키($0). 락 데이터에 CMK 과투자.
#tfsec:ignore:aws-dynamodb-enable-recovery
#tfsec:ignore:aws-dynamodb-table-customer-key
resource "aws_dynamodb_table" "tflock" {
  name         = var.lock_table_name
  billing_mode = "PAY_PER_REQUEST" # 온디맨드 = 유휴 시 $0
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  # 암호화를 코드로 명시 (DynamoDB는 기본 암호화되지만 의도를 박아 tfsec HIGH 해소).
  # enabled=true = AWS 관리 KMS 키 사용, 키 요금 $0 (CMK 아님).
  server_side_encryption {
    enabled = true
  }
}
