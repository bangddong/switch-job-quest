# 0-bootstrap이 만든 S3 버킷/DynamoDB 락에 이 레이어의 state를 둔다.
# key에 레이어명 prefix를 줘 0-bootstrap과 한 버킷을 공유하되 충돌 없음.
terraform {
  backend "s3" {
    bucket         = "devquest-eks-tfstate-seoul"
    key            = "1-network/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "devquest-eks-tflock"
    encrypt        = true
  }
}
