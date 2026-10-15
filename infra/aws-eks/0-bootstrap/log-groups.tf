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
