# 0-bootstrap이 만든 S3 버킷/DynamoDB 락. key에 레이어명 prefix로 격리.
terraform {
  backend "s3" {
    bucket         = "devquest-eks-tfstate-seoul"
    key            = "2-cluster/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "devquest-eks-tflock"
    encrypt        = true
  }
}
