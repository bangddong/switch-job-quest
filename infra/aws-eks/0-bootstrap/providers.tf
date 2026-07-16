provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "devquest-eks"
      Layer     = "0-bootstrap"
      ManagedBy = "OpenTofu"
    }
  }
}

# 계정 ID 등은 하드코딩하지 않고 여기서 동적 참조 (public repo 유출 방지)
data "aws_caller_identity" "current" {}
