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

# ── RDS (⑧) ───────────────────────────────────────────────────
# 기존 컨벤션 준수: 이 레이어의 모든 변수는 default를 갖는다
# (2-cluster엔 terraform.tfvars.example이 없어, default가 없으면 로컬·CI 양쪽에서 프롬프트가 뜬다).

variable "db_instance_class" {
  description = <<-EOT
    RDS 인스턴스 클래스. db.t4g.micro = $0.025/hr (ap-northeast-2 실측, 2026-07-28).
    ⚠️ 이 계정엔 RDS 프리티어가 없다(2025-07 이후 신규계정은 크레딧 구조) — 단가가 그대로 청구된다.
    3시간 세션 기준 RDS 증분은 ~$0.086으로 무시할 수준.
  EOT
  type        = string
  default     = "db.t4g.micro"
}

variable "db_engine_version" {
  description = <<-EOT
    PostgreSQL 엔진 버전. ap-northeast-2에서 13~18 지원 확인(describe-db-engine-versions 실측).
    🟡 prod(Neon)의 메이저 버전은 미확인 상태다. Flyway 마이그레이션 검증 가치를 높이려면
    prod와 메이저를 맞추는 편이 낫다 — TASKS.md의 Neon 버전 확인 항목 참조.
  EOT
  type        = string
  default     = "17.10"
}

variable "db_name" {
  description = "생성할 데이터베이스 이름 (application-prod.yml의 DB_NAME으로 주입됨)"
  type        = string
  default     = "devquest"
}

variable "db_master_username" {
  description = "RDS 마스터 사용자명. 비밀번호는 manage_master_user_password로 AWS가 생성/소유한다."
  type        = string
  default     = "devquest"
}

variable "github_client_id_placeholder" {
  description = <<-EOT
    학습 클러스터용 GitHub OAuth client id **자리표시 값**.
    🔴 prod의 실제 값을 넣지 말 것 — 학습장에 실서비스 신원 발급 권한을 주는 것이 된다.
    Stage 2의 목표는 앱이 완전히 부팅되는지(/health 200)이지 로그인 e2e가 아니다.
  EOT
  type        = string
  default     = "learning-placeholder-not-a-real-oauth-app"
}

variable "github_client_secret_placeholder" {
  description = "위와 동일. 학습 전용 자리표시 값이며 실제 시크릿이 아니다."
  type        = string
  default     = "learning-placeholder-not-a-real-secret"
}

# ── Grafana Loki (관측) 자리표시 3종 ──
# 🔴 prod Grafana 스택의 실제 URL·instance id·API 키를 **여기 넣지 말 것.**
#    ① 학습 클러스터에 실 크리덴셜을 두지 않는다 ② prod 로그 스트림이 학습 로그로 오염된다
#    ③ 이 레포는 퍼블릭이라 기본값이 그대로 공개된다.
# URL은 문법적으로 유효해야 한다 — loki4j가 시작 시 URI를 파싱하기 때문.
# 전송은 실패하지만 비동기 경고로만 남고 앱은 정상 동작한다.
variable "grafana_loki_url_placeholder" {
  description = "학습 클러스터용 Loki push URL 자리표시. 도달 불가 주소를 의도적으로 사용한다."
  type        = string
  default     = "http://127.0.0.1:3100/loki/api/v1/push"
}

variable "grafana_loki_instance_id_placeholder" {
  description = "Loki instance id 자리표시. 학습 전용."
  type        = string
  default     = "0"
}

variable "grafana_api_key_placeholder" {
  description = "Grafana API 키 자리표시. 실제 키 아님."
  type        = string
  default     = "learning-placeholder-not-a-real-api-key"
}
