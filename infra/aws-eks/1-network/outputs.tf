# 2-cluster가 terraform_remote_state로 이 값들을 읽어 EKS를 배치한다.
output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "퍼블릭 서브넷 ID 목록 (EKS 클러스터·노드그룹 배치용)"
  value       = aws_subnet.public[*].id
}

# ── AZ → 서브넷 맵 (Stage 3b에서 신설) ────────────────────────────
#
# 왜 필요한가: **EBS는 AZ에 물리적으로 묶인 리소스**다. 2a에 만든 볼륨은 2c에 뜬
# 인스턴스에 붙지 않는다(케이블이 없다).
#
# 그런데 노드그룹에 서브넷을 둘 다 주면(`public_subnet_ids`) **어느 AZ에 노드가 뜰지
# AWS ASG가 정한다** — 코드에 pin이 없다. 노드가 1대뿐이라 매 세션 동전 던지기다.
#   Stage 3a가 안 터진 건 순전히 `volumeBindingMode: WaitForFirstConsumer` 덕이었다
#   (파드가 스케줄된 **뒤에** 그 AZ에 볼륨을 만드므로 항상 일치).
#   Stage 3b는 볼륨이 먼저 존재하므로 그 방어막이 없다 → 50% 확률로 영구 Pending.
#
# → 2-cluster가 "영속 볼륨이 있는 AZ의 서브넷 하나"만 골라 노드그룹에 줄 수 있게 맵으로 낸다.
#
# ℹ️ 컨트롤플레인(`cluster.tf`)은 계속 **두 AZ 전부**를 받아야 한다 — EKS API의 강제
#    요구사항(서로 다른 AZ 2곳 이상)이다. 좁히는 것은 노드그룹뿐이다.
output "public_subnet_ids_by_az" {
  description = "AZ → 퍼블릭 서브넷 ID. 영속 EBS와 같은 AZ에 노드를 고정할 때 쓴다."
  value       = { for s in aws_subnet.public : s.availability_zone => s.id }
}

output "azs" {
  description = "서브넷이 놓인 AZ 목록 (persistent_az 유효성 검증용)"
  value       = aws_subnet.public[*].availability_zone
}

output "vpc_cidr" {
  description = "VPC CIDR (보안그룹 규칙 등에 참조)"
  value       = aws_vpc.main.cidr_block
}
