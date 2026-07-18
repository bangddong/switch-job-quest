# remote backend — 이 스택이 스스로 만든 S3 버킷 + DynamoDB 락에 자신의 state를 둔다.
#
# 부트스트랩 닭-달걀 2단계:
#   1단계(완료): backend 블록 없이 로컬 state로 apply → 아래 버킷·테이블 생성
#   2단계(지금): 이 파일 추가 후 `tofu init -migrate-state` → 로컬 state를 S3로 이관
#
# ⚠️ backend 블록은 변수(var.*)를 못 쓴다 — tofu가 다른 무엇보다 먼저 state 위치를 알아야 하므로
#    값이 리터럴이어야 한다. 버킷/테이블 이름은 backend-state.tf·variables.tf의 default와 일치시킬 것.
terraform {
  backend "s3" {
    bucket         = "devquest-eks-tfstate-seoul"
    key            = "0-bootstrap/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "devquest-eks-tflock"
    encrypt        = true
  }
}
