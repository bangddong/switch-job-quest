variable "region" {
  type        = string
  description = "AWS 리전 (서울)"
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  type        = string
  description = "OpenTofu remote state용 S3 버킷 이름 (전역 유일해야 함 — 충돌 시 변경)"
  default     = "devquest-eks-tfstate-seoul"
}

variable "lock_table_name" {
  type        = string
  description = "state 락용 DynamoDB 테이블 이름"
  default     = "devquest-eks-tflock"
}
