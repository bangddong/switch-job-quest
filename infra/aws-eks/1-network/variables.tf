variable "region" {
  type        = string
  description = "AWS 리전 (서울)"
  default     = "ap-northeast-2"
}

# EKS 서브넷 자동 인식(discovery) 태그에 쓰인다. 2-cluster의 클러스터 이름과 반드시 일치시킬 것.
variable "cluster_name" {
  type        = string
  description = "EKS 클러스터 이름 (서브넷 kubernetes.io/cluster 태그에 사용)"
  default     = "devquest-eks"
}

variable "vpc_cidr" {
  type        = string
  description = "VPC CIDR 블록"
  default     = "10.0.0.0/16"
}

# 2개 AZ = EKS 컨트롤플레인 최소 요건. 각 AZ에 퍼블릭 서브넷 하나씩.
variable "azs" {
  type        = list(string)
  description = "서브넷을 배치할 가용영역"
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "public_subnet_cidrs" {
  type        = list(string)
  description = "퍼블릭 서브넷 CIDR (azs와 인덱스 대응). /20 = 각 4091 IP"
  default     = ["10.0.0.0/20", "10.0.16.0/20"]
}
