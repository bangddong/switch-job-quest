terraform {
  required_version = ">= 1.8.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.100"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    # ℹ️ `random` provider는 **제거됐다.** JWT_SECRET을 여기서 생성하던 유일한
    #    사용처가 0-bootstrap으로 옮겨갔기 때문이다(`0-bootstrap/jwt-secret.tf`).
    #    안 쓰는 provider를 남기면 "여기서 무언가를 생성한다"는 잘못된 신호가 되고,
    #    이 레포가 반복적으로 겪은 *서술과 코드의 불일치*가 하나 더 생긴다.
  }
}
