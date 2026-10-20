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
  description = "prod DB EC2 를 만들지. 🔴 true = 상시 과금(월 ≈ $13+). 2026-10-20 사용자 승인으로 켬(📌D-016 6-B)"
  # 🔴 tfvars 가 아니라 **기본값**으로 켠다(B6-27) — CI 는 TF_VAR 를 주입하지 않고 기본값으로 apply 한다.
  #    로컬 tfvars 로 켜면 다음 CI apply 가 false 기준으로 destroy 를 시도해 prevent_destroy 로 매번 빨갛게 된다.
  #    끄려면 이 값을 false 로 돌리는 것으로는 안 된다(prevent_destroy) — PERSISTENT-RESOURCES 의 제거 절차.
  default     = true
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

# ── 📌D-016 6-A: prod DB 백업 ─────────────────────────────────────────
# 🔴 버킷 이름은 0-bootstrap `backup_bucket_name` 의 **복제**다 — 바꿀 땐 두 곳을 함께 바꾼다.
#    remote_state 로 읽지 않는 이유: infra-deploy 가 0-bootstrap·1-network 를 **병렬** apply 해서
#    새 출력을 추가한 PR 에서 1-network 가 옛 state 를 읽고 실패한다(B6-9). 5-A 의 AZ 가 같은 판단.
variable "db_backup_bucket" {
  type        = string
  description = "prod DB 덤프를 올릴 S3 버킷 (0-bootstrap backup_bucket_name 과 같아야 한다)"
  default     = "devquest-eks-backups-seoul"
}

# 🔴 학습용 덤프(`postgres/`, db-backup.sh)와 **반드시 다른 prefix** — 같으면 `db-restore.sh --s3` 가
#    학습 클러스터에 prod 데이터를 복원할 수 있다(B6-6, N-17 역방향). 인스턴스 역할도 이 prefix 만.
variable "db_backup_prefix" {
  type        = string
  description = "prod DB 덤프·헬스 키의 S3 prefix (끝은 /)"
  default     = "prod-db/"

  validation {
    condition     = endswith(var.db_backup_prefix, "/") && var.db_backup_prefix != "postgres/"
    error_message = "db_backup_prefix 는 / 로 끝나야 하고 학습용 postgres/ 와 달라야 한다(B6-6)."
  }
}
