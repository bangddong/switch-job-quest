terraform {
  required_version = ">= 1.8.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    # GitHub OIDC 발급자의 TLS 인증서 지문을 하드코딩 대신 동적 조회 (지문 회전 대응)
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}
