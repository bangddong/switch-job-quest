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

# ── 📌D-016 순서표 5-A — prod DB (db.tf) ─────────────────────────────────
variable "db_enabled" {
  type        = bool
  description = "prod DB EC2 를 만들지. 🔴 true 로 바꾸는 머지 = 상시 과금 시작(월 ≈ $13+). 별도 PR·승인으로만"
  default     = false
}

variable "db_instance_type" {
  type        = string
  description = "prod DB 인스턴스 타입. Free Plan 은 free-tier-eligible 만 launch 된다(D-010, t4g.nano 거부 실측). 변경 = stop/start"
  default     = "t4g.micro"
}

variable "db_data_volume_gb" {
  type        = number
  description = "데이터 EBS 크기(GiB). 늘리기만 가능(줄이기 불가)"
  default     = 10
}

variable "db_postgres_major" {
  type        = string
  description = "Postgres 메이저 — Neon 과 같아야 한다(Q-2 매일 복원, B5-11). Neon = 17 (2026-10-20 확인)"
  default     = "17"
}

variable "db_ssm_prefix" {
  type        = string
  description = "DB 비밀이 사는 SSM 경로(password · wireguard-conf). IaC 밖에서 만들어진다"
  default     = "/devquest/prod/db"
}
