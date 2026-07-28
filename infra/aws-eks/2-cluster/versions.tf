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
    # JWT_SECRET을 세션마다 새로 생성하는 데 사용 (secrets.tf ⑨).
    # 생성값은 tfstate에 남지만, 학습 전용 크리덴셜이고 state는 S3에서 암호화된다.
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}
