# 1-network 레이어가 S3에 써둔 state를 읽어 VPC·서브넷을 참조한다.
data "terraform_remote_state" "network" {
  backend = "s3"
  config = {
    bucket = "devquest-eks-tfstate-seoul"
    key    = "1-network/terraform.tfstate"
    region = var.region
  }
}
