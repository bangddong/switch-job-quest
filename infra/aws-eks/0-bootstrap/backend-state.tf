# 이 스택이 만드는 S3 버킷 + DynamoDB 락 테이블 = 모든 레이어(및 자기 자신)의 remote backend.
#
# 부트스트랩 닭-달걀:
#   1) backend 블록 없이(=로컬 state) `tofu apply` → 아래 버킷·테이블 생성
#   2) backend.tf 활성화 후 `tofu init -migrate-state` → 로컬 state를 S3로 이관
#
# ⚠️ public repo: state 파일에는 평문 secret이 들어갈 수 있음 → *.tfstate는 절대 커밋 금지(.gitignore).

resource "aws_s3_bucket" "tfstate" {
  bucket = var.state_bucket_name
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  versioning_configuration {
    status = "Enabled"
  }
}

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

resource "aws_dynamodb_table" "tflock" {
  name         = var.lock_table_name
  billing_mode = "PAY_PER_REQUEST" # 온디맨드 = 유휴 시 $0
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}
