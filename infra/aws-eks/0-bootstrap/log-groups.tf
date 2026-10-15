# ── CloudWatch 로그 그룹 — 학습 클러스터의 로그를 클러스터 밖에 남긴다 (Stage 5d) ─────────
#
# 왜 0-bootstrap(영속 레이어)에 두나:
#   로그의 목적은 "파드·노드·클러스터가 사라진 뒤에도 읽는 것"이다. 2-cluster 에 두면 destroy 가 그룹째 지운다.
#   1-network 는 수명이 길 뿐 영속을 보장하는 레이어가 아니다(README 레이어 표). ECR·S3 백업·ACM 과 같은 판단.
#
# 무엇이 들어오나 (보내는 쪽은 k8s/logging/ 의 Fluent Bit 두 벌):
#   containers — 각 노드의 컨테이너 stdout 파일(DaemonSet). 스트림 = 네임스페이스.파드.컨테이너
#   events     — K8s 이벤트(Deployment 1개, API watch). 스트림 = k8s-events
#
# 🔴 그룹은 **여기서만** 만든다. 수집기의 IRSA 정책(2-cluster/irsa-fluent-bit.tf)에 CreateLogGroup 이 없고,
#    수집기 설정도 auto_create_group Off 다. 이름을 바꾸면 수집기가 쓸 곳을 잃고 **실패한다** — 조용히 새 그룹
#    (보존 무기한 · state 밖)을 만들지 않게 일부러 그렇게 했다.
#
# 비용 (2026-10-14 `aws pricing get-products` 서울 실측): 수집 $0.76/GB · 저장 $0.0314/GB-월 ·
#   Logs Insights 스캔 $0.0076/GB. 빈 그룹은 $0. 보존 30일이라 저장량이 무한히 늘지 않는다.
#
# 세션 전에 이 레이어를 **로컬에서 먼저 apply** 한다 — CI 는 main 머지 때만 apply 하는데 세션은 브랜치에서 하므로,
#   그러지 않으면 세션 중에 그룹이 없다(계획서 D5). 상태는 S3 원격 백엔드라 머지 뒤 CI 는 `No changes` 가 된다.

locals {
  learning_log_groups = {
    containers = "/devquest/eks-learning/containers"
    events     = "/devquest/eks-learning/events"
  }
}

# tfsec:ignore:aws-cloudwatch-log-group-customer-key — 학습장: KMS CMK 는 월 $1+. CloudWatch Logs 는 기본으로 서버 측 암호화된다. ecr.tf · backend-state.tf 의 KMS 생략과 같은 판단
resource "aws_cloudwatch_log_group" "learning" {
  for_each = local.learning_log_groups

  name = each.value

  # 30일 — 세션 뒤 퀴즈·복기를 덮는 기간(계획서 D4). 🔴 0(무기한)으로 두지 말 것:
  #   계정에 이미 보존 무기한으로 남은 고아 그룹이 하나 있다(PERSISTENT-RESOURCES.md 「표 밖에서 발견된 것」).
  retention_in_days = 30
}

# ── EKS 컨트롤플레인 로그 그룹 (Stage 5f) ───────────────────────────────────────
#
# EKS 가 API 서버 · 감사(audit) · 인증(authenticator) · controller-manager · scheduler 로그를 보내는 곳.
# 🔴 이름을 우리가 정할 수 없다 — EKS 는 **`/aws/eks/<클러스터 이름>/cluster`** 에만 쓴다.
#    그룹이 없으면 EKS 가 **스스로 만든다 — 보존 무기한 · state 밖**이다(실패 6종의 "조용한 고아" 형태).
#    그래서 클러스터를 만들기 전에 여기서 먼저 만들어 둔다. 있으면 EKS 는 그것을 쓴다.
#
# 🔴 위 learning_log_groups 맵에 넣지 않는다 — 2-cluster/irsa-fluent-bit.tf 가 그 맵의 **모든 값**에 쓰기 권한을 준다.
#    수집기가 컨트롤플레인 감사 로그에 쓸 수 있게 되는 것은 의도가 아니다. EKS 는 서비스 연결 역할로 쓰므로 IAM 이 필요 없다.
#
# 클러스터 이름은 2-cluster 의 var.cluster_name 기본값과 같아야 한다. 0-bootstrap 은 그 변수를 모르므로 여기 적고,
#   2-cluster/cluster.tf 의 precondition 이 "로그를 켰는데 이름이 다르다"를 plan 에서 막는다.
locals {
  eks_cluster_name = "devquest-eks"
}

# tfsec:ignore:aws-cloudwatch-log-group-customer-key — 위 learning 그룹과 같은 판단
resource "aws_cloudwatch_log_group" "eks_control_plane" {
  name              = "/aws/eks/${local.eks_cluster_name}/cluster"
  retention_in_days = 30 # 🔴 0(무기한) 금지 — 위와 같은 이유
}
