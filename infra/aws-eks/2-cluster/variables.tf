variable "region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "cluster_name" {
  description = "EKS 클러스터명 (1-network 서브넷 태그와 일치해야 함)"
  type        = string
  default     = "devquest-eks"
}

variable "kubernetes_version" {
  description = "EKS K8s 버전 (apply 직전 표준 지원 최신으로 재확인)"
  type        = string
  default     = "1.36"
}

variable "node_instance_type" {
  description = "노드 인스턴스 타입 (ARM Graviton)"
  type        = string
  default     = "t4g.small"
}

variable "node_capacity_type" {
  description = <<-EOT
    노드그룹 용량 타입. 기본 ON_DEMAND — 신규 계정의 Spot vCPU 쿼터가 0이라
    SPOT으로 두면 apply가 InsufficientInstanceCapacity/쿼터 오류로 실패한다.
    스팟 학습 시 쿼터 증액(Service Quotas: All G and VT Spot Instance Requests 등)을
    받은 뒤 var로 SPOT을 주입한다. 온디맨드↔스팟 비용차는 650h 기준 약 $13에 불과.
  EOT
  type        = string
  default     = "ON_DEMAND"

  validation {
    condition     = contains(["ON_DEMAND", "SPOT"], var.node_capacity_type)
    error_message = "node_capacity_type must be ON_DEMAND or SPOT."
  }
}

variable "node_desired_size" {
  description = "노드그룹 희망 노드 수"
  type        = number
  default     = 1
}

variable "node_min_size" {
  description = "노드그룹 최소 노드 수"
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "노드그룹 최대 노드 수"
  type        = number
  default     = 2
}
