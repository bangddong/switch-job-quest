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
  description = "노드 인스턴스 타입 (ARM Graviton). 🔴 free-tier-eligible 타입만 launch 가능한 계정이다"
  type        = string

  # 🔴 t4g.medium → t4g.small **되돌림** (2026-08-31 유료 세션 실측. D-009 정정 = D-010).
  #
  # **D-009(medium 상향)는 방향은 옳았으나 실행 불가능한 수단이었다.** 단가·파드공식은 맞게 따졌는데
  # **계정이 그 타입을 띄울 수 있는지**를 확인하지 않았다. 이 계정은 신 Free Tier 플랜이라
  # `free-tier-eligible=true` 인 타입만 launch 가 허용된다.
  #
  # 실패는 EKS 층에 안 보였다 — `describe-nodegroup` 의 `health.issues` 가 **빈 배열**이라
  # "그냥 느린 것"과 구별되지 않았고 10분을 태웠다. 증거는 **ASG 활동 로그에만** 있었다:
  #   aws autoscaling describe-scaling-activities --auto-scaling-group-name eks-devquest-eks-ng-...
  #   StatusCode: Failed (5회) — "InvalidParameterCombination - The specified instance type
  #                              is not eligible for Free Tier."
  # 👉 다음에 노드가 안 뜨면 **ASG 활동 로그를 먼저 본다.**
  #
  # 📏 free-tier-eligible 전수 (aws ec2 describe-instance-types --filters Name=free-tier-eligible,Values=true):
  #     arm64 : t4g.micro 1024 · **t4g.small 2048**            ← arm64 는 2 GiB 가 상한
  #     x86_64: t3.micro 1024 · t3.small 2048 · c7i-flex.large 4096 · m7i-flex.large 8192
  #   → **메모리를 늘리려면 x86_64 로 가야 하고, 그러면 이미지 재빌드가 선행 조건이다**
  #     (현재 전부 arm64: ecr-push.yml `runs-on: ubuntu-24.04-arm`, nodes.tf `AL2023_ARM_64_STANDARD`).
  #
  # 📏 **Allocatable 실측 (2026-08-31) — 이 주석이 "미확인"으로 남겨뒀던 바로 그 값이다:**
  #     kubectl get node -o json → t4g.small
  #       capacity    : memory 1885252Ki (1841Mi) · cpu 2    · pods 11
  #       allocatable : memory 1397828Ki (1365Mi) · cpu 1930m · pods 11
  #     ⚠️ 공칭 2 GiB 가 아니라 **capacity 부터 1841Mi** 다(커널·펌웨어 예약). 거기서 kubelet/system
  #        예약이 **476Mi(26%)** 를 더 뗀다. "2 GiB 노드"에서 출발해 추정하면 500Mi 가까이 과대평가한다.
  #     시스템 파드 requests 가 이미 406Mi(29%) 를 점유(aws-node·coredns·ebs-csi×2·kube-proxy).
  #     → **우리 워크로드 가용 = 1365 - 406 = 959Mi.**
  #        필요 = postgres 256 + 앱 512×3 = **1792Mi** → **-833Mi 부족.**
  #
  # 🔴 결론(산술이지 추정이 아니다): t4g.small 은 **앱 1개 + postgres(768Mi)가 상한**이다.
  #     앱 2개만 돼도 1280Mi 로 초과한다. Stage 3b 가 core-api 하나만 띄운 게 우연이 아니었다.
  #     **파드 슬롯은 제약이 아니었다** — 11칸 중 5칸 사용, 6칸 여유. 필요한 건 4칸. CPU 도 1590m 여유.
  #     C-4 의 *"메모리가 먼저 막는다"* 는 맞았고 이제 숫자가 붙었다.
  #
  # 🔑 **왜 기본값을 medium 이 아니라 small 로 두는가**: medium 은 이 계정에서 **launch 자체가 안 된다.**
  #     띄울 수 없는 값을 기본값으로 두면 다음 사람이 같은 벽에 부딪히며 과금 중에 10분을 태운다.
  #     "동작하는 값"을 기본으로 두고, 늘리는 경로는 아래 주석으로 안내한다.
  #
  # ~~기각: 노드 2대~~ → 🔴 **기각 철회 (2026-08-31)**, 이후 **3대로 상향 (2026-09-03)**. 아래 "재개 경로" 참조.
  #     D-009 의 기각 사유 중 *"파드당 메모리 벽은 그대로"* 는 **논점 이탈**이었다
  #     (스케일 아웃이 푸는 것은 총량이다). *"medium 보다 비싸다"* 는 실측으로 무효가 됐다.
  #     남아 있는 유효한 사유는 *"단일 AZ 라 가용성 이득 없음"* 뿐인데, 학습 클러스터에서
  #     가용성은 목표가 아니다. DaemonSet 3개가 새 노드 자리를 먼저 먹는 것은 사실이나
  #     파드 슬롯은 애초에 제약이 아니었다(11칸 중 6칸 여유).
  #
  # ▶ 3서비스로 가려면 (Stage C 재개 경로) — 🔴 2026-09-03 **재정정: 2대 → 3대**
  #
  #   **1순위: 노드 3대.** `-var node_desired_size=3` 한 줄. ami_type 은 arm64 그대로라
  #     **이미지 재빌드 불필요**. 이 파일에서 바뀐 것은 아래 `node_max_size` 2 → 3 뿐이다.
  #
  #   🔴 **왜 2대가 아니라 3대인가 — 2대는 안전을 증명할 수 없다** (2026-09-03, $0 산술):
  #     필요 1792Mi (postgres 256 + 앱 512×3). 노드당 allocatable **1365Mi**(08-31 실측).
  #     406Mi 의 DaemonSet ÷ Deployment 분리가 **아직 미측정**이므로 양극단을 모두 따진다.
  #
  #     ① 비관 (406Mi 전부가 노드당 DaemonSet → 모든 노드 여유 959Mi)
  #         2대: 512→A(959→447) · 512→B(959→447) · 세 번째 512 는 **어느 쪽도 안 들어감 → Pending**
  #         3대: 512→A · 512→B · 512→C · 256→아무 노드(각 447 여유)              → **성공**
  #     ② 낙관 (DaemonSet ≈ ebs-csi-node 120Mi 뿐 → A 959 · B 1245, 합 2204)
  #        🟡 **120Mi 는 추정이다** — ebs-csi-node 3컨테이너 × 40Mi 로 봤고, aws-node·kube-proxy 는
  #           메모리 requests 가 없다고 가정했다. **교차검증**: 이 모델의 CPU 합 315m 이 실측 340m 에
  #           근접한다. 메모리는 직접 확인하지 못했다(→ 아래 "측정할 것").
  #           🔑 **결론은 ① 비관 가정으로 냈으므로 이 추정이 틀려도 3대 판단은 안 바뀐다.**
  #         2대: 스케줄 **순서에 따라** 갈린다. 예: pg→B, core-api→B, ai-api→A 순이면
  #              daily-api 차례에 A 447 · B 477 로 **둘 다 부족 → Pending**
  #
  #     👉 **3대는 비관 가정에서도 성립한다** = 결과가 미측정 값에 의존하지 않는다.
  #        2대는 어느 가정에서도 "안전하다"고 말할 수 없다. 차액은 ~~50분 세션당 $0.021~~
  #        → **$0.0236** 이다 (09-04 정정 — 아래 루트 EBS 누락 참조)
  #        (노드 1대 = EC2 $0.0208/h + 공인 IPv4 $0.005/h ~~= $0.0258/h~~
  #         🔴 **+ 루트 EBS 20GiB $0.0025/h = $0.0283/h** — 2026-09-04 퀴즈 중 사용자가 지적.
  #         `nodes.tf` 에 `disk_size` 가 없어 EKS 기본값 20GiB 가 노드마다 붙는다(🟡 문서 기본값, 미실측).
  #         ⚠️ **정정할 때마다 다른 항목을 빠뜨렸다** — D-009 는 IPv4 를 넣었고, 08-31 "정정"은
  #            IPv4 하나를 빠뜨렸고, 09-03 "재정정"은 루트 EBS 를 빠뜨렸다. 결론은 안 바뀐다).
  #        검증 불가능한 스케줄링 제약을 **과금 중에** 거는 것보다 2센트로 여유를 사는 쪽이 낫다.
  #
  #   🔑 **스케줄러는 오버커밋하지 않는다.** 종전 서술 *"core-api+ai-api 가 한 노드에 얹히면
  #      1024Mi > 959Mi 로 즉시 초과"* 는 **틀렸다** — 안 맞는 노드에는 애초에 배치되지 않는다.
  #      실제 증상은 OOM 이 아니라 `Pending / Insufficient memory` 이고, 위험은 초과가 아니라
  #      **조각화**다(총량은 남는데 512Mi 들어갈 연속 자리가 없는 상태).
  #
  #   🔑 **`podAntiAffinity` 만으로는 2대를 구제하지 못한다** — 원장 L-43 의 처방이 불충분했다.
  #      앱을 tier 로 갈라도 **시스템 Deployment**(coredns ~70Mi + ebs-csi-controller ~240Mi)가
  #      어느 노드에 앉을지는 여전히 통제되지 않는다. 그 둘이 ai tier 노드에 앉으면 여유가
  #      935Mi 로 떨어져 1024Mi 가 **또 안 들어간다.** 노드를 진짜로 지정하려면 노드 라벨이 필요한데
  #      단일 노드그룹은 노드마다 다른 라벨을 줄 수 없으므로 **노드그룹을 쪼개야** 한다.
  #      3대로 가면 이 인프라 변경이 통째로 불필요해진다.
  #
  #   📌 그래도 **측정할 것** (단 블로커는 아니다): 406Mi 의 DaemonSet ÷ Deployment 분리
  #      (`kubectl get pods -A -o json`). 3대 결정은 이 값과 무관하지만 coredns 실제 requests 등
  #      남은 추정치를 확정해 준다. 원장 L-43.
  #
  #   📌 ESO 3파드는 requests 가 **비어 있다** (`helm show values external-secrets/external-secrets
  #      --version 2.8.0` → `resources: {}` ×3, 2026-09-03 실측). 스케줄러 예산은 0 이지만
  #      **BestEffort** 라 노드 메모리 압박 시 **가장 먼저 evict** 된다.
  #      파드 슬롯은 3칸 먹는다(3대 = 33칸, 총 15칸 사용 → 여유 충분).
  #
  #   **2순위(1순위 실패 시): x86 전환.** 비용 2배 이상 + arm64 상실 → 먼저 쓸 카드가 아니다.
  #     ① ecr-push.yml `runs-on` → x86 러너, 이미지 3개 재빌드
  #     ② nodes.tf `ami_type` → AL2023_x86_64_STANDARD
  #     ③ 이 기본값 → c7i-flex.large(4 GiB) 또는 m7i-flex.large(8 GiB)
  #     ④ ①~③ 은 전부 $0 구간에서 끝낸 뒤 apply 할 것
  #
  # 🔑 **현재 기본값은 1대**이고, 그래서 addons.tf 의 coredns replicaCount=1 규율이 유효하다.
  #    ⚠️ 위 "1순위: 노드 3대" 를 실행하는 순간 그 규율의 전제가 깨진다 — addons.tf 가
  #       *"노드 2대 이상이면 coredns 를 2 로 되돌릴 것"* 이라고 스스로 적어뒀다. 같이 볼 것.
  #       3대에서는 되돌려도 안전하다 — 비관 가정(노드당 여유 959Mi)에서도 앱 하나 512Mi 를
  #       얹고 447Mi 가 남으므로 coredns 두 번째 replica(~70Mi)를 흡수한다.
  #
  default = "t4g.small"
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

  # 🔴 2 → 3 (2026-09-03). Stage C 재개 경로가 **노드 3대**로 바뀌었기 때문이다
  #    (위 node_instance_type 주석의 산술 참조 — 2대는 비관 가정에서 Pending 이 증명된다).
  #    이 값이 2 로 남아 있으면 `-var node_desired_size=3` 이 ASG 제약에 걸려 apply 가 죽는다.
  #    ⚠️ 상한만 올린다. `node_desired_size` 기본값은 **1 그대로** 두므로 이 커밋으로
  #       비용이 늘지 않는다 — 3대는 세션에서 `-var` 로 명시할 때만 뜬다.
  default = 3
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
