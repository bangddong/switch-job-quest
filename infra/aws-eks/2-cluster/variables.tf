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

  # t4g.small → t4g.medium 상향 (2026-08-31, Phase 2 Stage C · 결정 D-009).
  #
  # 왜: JVM 앱이 1개(core-api) → 3개(core-api·ai-api·daily-api)가 된다. small 로는 **파드도 메모리도**
  #     모자란다. 파드 베이스라인 10/11(Stage 3b 의 coredns replicaCount=1 반영) → 2개 추가 시 12 > 11.
  #     🔴 그리고 파드 상한보다 **메모리가 먼저 막는다** — Stage 3a 실측 스케줄러 메시지가
  #     `Insufficient memory, Too many pods` **둘 다**였다(docs/eks-migration-log.md).
  #     requests 합 추정 1792Mi(512×3 + postgres 256) 는 small 물리 2048MiB 에 안 들어간다.
  #
  # 기각: **노드 2대**(node_desired_size=2). 파드 슬롯만 풀고 **파드당 512Mi 메모리 벽은 그대로**다.
  #     nodes.tf 가 subnet_ids 를 persistent_az **단일 AZ 로 핀**해 2대가 같은 AZ 에 뜨므로
  #     가용성 이득도 없다. DaemonSet 3개가 새 노드 자리를 먼저 먹어 순증은 11이 아니라 8이고,
  #     addons.tf 의 자기 규율("노드 2대 이상이면 coredns 를 2로 되돌릴 것")까지 따르면 7.
  #     비용도 +$0.026/h 로 medium(+$0.0208/h)보다 **비싸다**.
  #
  # 📏 실측 (2026-08-31, AWS API 조회 — 클러스터 미가동, 무료):
  #     aws ec2 describe-instance-types --instance-types t4g.small t4g.medium
  #       t4g.small  : ENI 3 × IPv4 4 → 파드 3×(4-1)+2 = 11  · 2048 MiB
  #       t4g.medium : ENI 3 × IPv4 6 → 파드 3×(6-1)+2 = 17  · 4096 MiB
  #     ↑ small 의 11 이 기존 2회 실측과 일치 → **공식이 검증된 상태에서** medium 17 을 얻었다.
  #     aws pricing get-products (Seoul, Linux, Shared, OnDemand):
  #       t4g.small $0.0208/h · t4g.medium $0.0416/h (정확히 2배)
  #     → 세션 총액 $0.13/h → **$0.149/h**. ⚠️ 일지의 "$0.16" 은 근거 미기재 추정이었고 $0.011 과다였다.
  #
  # ⚠️ 아직 미확인: 노드 `Allocatable.memory`(kube-reserved·eviction 제외 후 실제 가용량).
  #     레포에 기록 0건이라 위 메모리 판정은 여전히 추정이다 — **다음 apply 세션에서
  #     `kubectl describe node` 로 재고 이 주석과 원장을 갱신할 것.**
  #
  # 🔑 노드는 **1대 유지**다. addons.tf 의 coredns replicaCount=1 규율은 그대로 둔다.
  default = "t4g.medium"
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

# ── DB 모드 (Stage 2 ↔ 3a 전환 스위치) ─────────────────────────
#
# 왜 rds.tf를 지우지 않고 토글로 두나 (D-001):
#   지우면 **튜토리얼 Stage 2가 재현 불가**가 된다. 이 레포의 목적 중 하나가
#   "처음 하는 사람이 그대로 따라 할 수 있는 문서"이므로, 지나온 Stage를
#   코드에서 삭제하면 문서만 남고 실행은 안 되는 상태가 된다.
#
# 🔴 **기본값이 `in-cluster`인 이유 — 안전한 쪽으로 실패하게 만든다.**
#   반대로 기본을 `rds`로 두면, 앞으로 모든 세션이 `-var db_mode=in-cluster`를 붙여야 하고
#   **깜빡한 순간 RDS가 생성돼 과금된다**($0.025/h). 플래그를 잊었을 때 일어나는 일이
#   "돈이 나간다"가 되면 안 된다.
#   반면 기본을 in-cluster로 두면 잊었을 때 일어나는 일은 "Stage 2 재현이 안 된다"뿐이고,
#   Stage 2 재현은 **의도적 행위**라 플래그를 명시하는 게 오히려 맞다.
#     tofu apply -var db_mode=rds        # Stage 2 재현
#     tofu apply                          # Stage 3a 이후 (기본)
variable "db_mode" {
  description = <<-EOT
    DB를 어디에 둘지. rds = 관리형(Stage 2) / in-cluster = Postgres StatefulSet(Stage 3a~).
    기본은 in-cluster — 플래그를 잊어도 과금 리소스가 생기지 않는 쪽이 기본이어야 한다.
  EOT
  type        = string
  default     = "in-cluster"

  validation {
    condition     = contains(["rds", "in-cluster"], var.db_mode)
    error_message = "db_mode must be \"rds\" or \"in-cluster\"."
  }
}

# ℹ️ in-cluster Postgres의 볼륨 크기는 **여기 없다.** 동적 프로비저닝에서 크기를 정하는 주체는
#    terraform이 아니라 PVC이기 때문 — `k8s/base/postgres.yaml`의 volumeClaimTemplates에 있다.
#    (여기 변수를 두면 소비처 없는 죽은 설정이 된다. #326에서 두 번 걷어낸 유형.)
#
# 🔴 **정정 (Stage 3b, 07-31).** ~~"3b에서 EBS를 terraform 소유로 옮기면 그때 이 레이어로 올라온다"~~
#    → **틀렸다. `0-bootstrap`으로 갔다.** 여기(2-cluster)로 올렸다면 리퍼(dead man's switch)가
#    사람 부재 2시간 뒤 `tofu destroy -auto-approve`로 6개월치 데이터를 지웠을 것이다.
#    `prevent_destroy`로 막는 것도 답이 아니다 — destroy **전체**가 plan 단계에서 거부돼
#    EKS·노드·NAT까지 아무것도 안 지워지고, 리퍼가 30분마다 같은 에러를 무한 반복한다
#    (그 로그를 읽으려면 사람이 있어야 하는데, 리퍼 발동은 사람이 없다는 뜻이다).
#    → 크기 변수도 함께 `0-bootstrap/variables.tf`의 `postgres_volume_size_gb`에 있다.
#    상세: `.claude/CONTEXT.md` D-004 · `infra/aws-eks/0-bootstrap/ebs-postgres.tf`

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

# ── Grafana(관측) 자리표시 3종은 삭제됐다 ──
# 🔴 되살리지 마라. **자리표시를 넣는 것 자체가 문제였다.**
#    "도달 불가 주소를 넣어두면 안전하다"고 봤지만, 두 관측 경로 모두 스위치가
#    `enabled` 플래그가 아니라 **값의 존재 여부**여서 비어있지 않은 더미가 곧 ON이었다:
#      · Loki   → `<if property("grafanaLokiUrl").length() > 0>`가 참 → 어펜더 부착 → 무한 재시도
#      · 메트릭 → 더미 키 + prod yml의 **진짜** instance-id → 실제 Grafana Cloud로 60초마다 인증 시도
#    관측을 끄고 싶으면 **값을 주지 않는 것**이 유일한 올바른 방법이다.
# 🔴 실제 URL·instance id·API 키도 물론 넣지 말 것 — 이 레포는 퍼블릭이라 기본값이 그대로 공개된다.
#    (근거·전제는 secrets.tf ⑨의 주석 참조)
