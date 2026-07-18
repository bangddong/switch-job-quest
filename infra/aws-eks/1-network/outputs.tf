# 2-cluster가 terraform_remote_state로 이 값들을 읽어 EKS를 배치한다.
output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "퍼블릭 서브넷 ID 목록 (EKS 클러스터·노드그룹 배치용)"
  value       = aws_subnet.public[*].id
}

output "vpc_cidr" {
  description = "VPC CIDR (보안그룹 규칙 등에 참조)"
  value       = aws_vpc.main.cidr_block
}
