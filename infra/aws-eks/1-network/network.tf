# EKS용 최소 네트워크 — 퍼블릭 서브NET 전용(NAT 회피). 노드는 공인IP로 IGW 경유 인터넷.

# VPC. DNS hostnames/support는 EKS 노드 등록·프라이빗 엔드포인트에 필수라 켠다.
# tfsec:ignore:aws-ec2-require-vpc-flow-logs-for-all-vpcs — flow logs는 CloudWatch 비용 발생.
#   학습장(destroy-after-use)엔 과함. prod면 켠다.
#tfsec:ignore:aws-ec2-require-vpc-flow-logs-for-all-vpcs
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "${var.cluster_name}-vpc" }
}

# 퍼블릭 서브넷 → 인터넷 (무료). NAT Gateway($32/mo) 대체.
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.cluster_name}-igw" }
}

# 퍼블릭 서브넷 ×2 (2 AZ). map_public_ip_on_launch=true = 여기 뜨는 노드는 공인IP 획득.
# tfsec:ignore:aws-ec2-no-public-ip-subnet — 의도적. NAT 없이 노드가 인터넷 도달하려면 공인IP 필요.
#   보안은 노드 보안그룹으로 제어(2-cluster). prod면 private 서브넷 + NAT/endpoint.
#tfsec:ignore:aws-ec2-no-public-ip-subnet
resource "aws_subnet" "public" {
  count                   = length(var.public_subnet_cidrs)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.cluster_name}-public-${var.azs[count.index]}"
    # EKS 서브넷 자동 인식(discovery) 태그
    "kubernetes.io/role/elb"                    = "1"      # 인터넷 대면 로드밸런서용
    "kubernetes.io/cluster/${var.cluster_name}" = "shared" # 이 클러스터가 서브넷 공유
  }
}

# 퍼블릭 라우트: 모든 외부 트래픽 → IGW
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.cluster_name}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}
