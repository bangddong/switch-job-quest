# ══ 📌D-016 순서표 5-A — prod DB: EC2 자가 운영 Postgres 17 (상시, Fly·EKS 공유) ══
#
# 계획서 `docs/superpowers/plans/2026-10-16-prod-eks-weekday-window.md` 「5번」. 요약:
#
#   Fly(nrt, 밤·주말 prod) ──WireGuard(Fly 6PN 피어)──▶ EC2 ◀── VPC 사설 IP ── EKS(평일 창 prod)
#
#   · **5432 는 인터넷에 열지 않는다.** SG 인바운드 = `vpc_cidr`:5432 하나뿐. Fly 는 EC2 가 **바깥으로**
#     맺은 WireGuard 터널로 들어온다(5-0 실측: 인바운드 0개로 Fly→DB 접속 성공).
#   · EIP 없음 — 퍼블릭 IP 로 들어오는 주체가 없다(B5-4). 자동 할당 IP 는 터널 출발용.
#   · 비밀번호는 **EC2 가 첫 부팅 때 만들어 SSM SecureString 에 쓴다**(덮어쓰기 금지). tofu state 에
#     없다 — PR plan 역할이 이 레이어 state 를 읽기 때문이다(B3-6·B5-1). 그래서 SSM 파라미터를
#     tofu 리소스로 만들지도 않는다(refresh 가 `ssm:GetParameter` → PR 역할 Deny → plan 실패).
#
# 🔴 **이 레이어는 머지 = 자동 apply 다**(`infra-deploy.yml`). 그래서:
#   ① `db_enabled` 기본 false — 이 코드를 머지해도 아무것도 생기지 않는다. 켜는 것은 **별도 PR + 승인**
#      (상시 과금 시작 — 계획서: 6번 직전).
#   ② 켠 뒤 `false` 로 되돌리면 destroy 를 시도하고 `prevent_destroy` 가 apply 를 빨갛게 막는다 — 의도다.
#   ③ `ignore_changes = [ami, user_data]` — 새 AMI 나 스크립트 수정이 **무인 replace·stop/start** 를
#      일으키지 않게(B5-18). `instance_type` 변경은 여전히 stop/start 다 — 바꾸지 마라(바꿀 거면 창 밖 수동).
#   ④ 데이터는 **별도 EBS**(인스턴스가 사라져도 남는다). 루트 볼륨엔 OS·설정만.

locals {
  db_count = var.db_enabled ? 1 : 0
  # 노드그룹·Karpenter 와 같은 AZ(2a) — EKS→DB cross-AZ 전송 과금 회피(B5-7). 0-bootstrap 의
  # `persistent_az` 를 읽으면 1-network→0-bootstrap 의존이 생기고 infra-deploy 는 두 레이어를 병렬로
  # apply 하므로, 이 레이어 자신의 `azs[0]` 을 쓴다(둘 다 2a — 바꿀 때 함께 바꿀 것).
  db_az        = var.azs[0]
  db_subnet_id = aws_subnet.public[0].id
}

# Ubuntu 24.04 arm64 — 5-0 실측과 같은 배포판. PGDG 저장소로 Postgres 17(Neon 과 같은 메이저 — B5-11).
# 🔴 SSM 공개 파라미터로 AMI 를 고르지 마라 — PR 역할이 `ssm:GetParameter` 를 Deny 한다(B5-2).
data "aws_ami" "ubuntu_arm" {
  most_recent = true
  owners      = ["099720109477"] # Canonical
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-arm64-server-*"]
  }
}

# ── 보안그룹 — VPC 안에서 5432 만 ─────────────────────────────────────────
# rds.tf(학습 RDS)는 소스를 **클러스터 SG ID** 로 잡았다. 여기선 `vpc_cidr` 다 — 이 DB 는 상시이고
# 클러스터는 매일 destroy 되는데, SG ID 를 참조하면 클러스터 SG 삭제가 DependencyViolation 으로 막힌다(N-6·B5-6).
# ⚠️ description 은 ASCII 만 — 한글이면 plan·tfsec 는 통과하고 apply 에서야 실패한다(B5-17).
resource "aws_security_group" "db" {
  count       = local.db_count
  name        = "${var.cluster_name}-prod-db"
  description = "prod postgres - 5432 from VPC only; Fly reaches via outbound WireGuard"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "postgres from VPC (EKS nodes/pods)"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  # 이그레스: Fly WireGuard 게이트웨이(UDP 51820), 패키지 저장소, SSM 엔드포인트.
  #tfsec:ignore:aws-ec2-no-public-egress-sgr — 퍼블릭 서브넷·NAT 없음. 터널·패키지·SSM 이 인터넷 경유
  egress {
    description = "all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.cluster_name}-prod-db", Role = "prod-db" }
}

# ── 인스턴스 프로파일 — SSM 접속(SSH 포트 없음) + 비밀 파라미터 2개 ─────────────
resource "aws_iam_role" "db" {
  count = local.db_count
  name  = "${var.cluster_name}-prod-db"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy_attachment" "db_ssm_core" {
  count      = local.db_count
  role       = aws_iam_role.db[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_caller_identity" "current" {}

resource "aws_iam_role_policy" "db_params" {
  count = local.db_count
  name  = "db-params"
  role  = aws_iam_role.db[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # password: 첫 부팅에 PutParameter(덮어쓰기 없이), 재설치 시 GetParameter.
        # wireguard-conf: 운영자가 넣고(`fly wireguard create` 산출물 — 개인키) EC2 는 읽기만.
        Sid      = "DbParams"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:PutParameter"]
        Resource = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${var.db_ssm_prefix}/*"
      },
      {
        Sid      = "SsmKms"
        Effect   = "Allow"
        Action   = ["kms:Decrypt", "kms:Encrypt", "kms:GenerateDataKey"]
        Resource = "*"
        Condition = {
          StringEquals = { "kms:ViaService" = "ssm.${var.region}.amazonaws.com" }
        }
      },
    ]
  })
}

resource "aws_iam_instance_profile" "db" {
  count = local.db_count
  name  = "${var.cluster_name}-prod-db"
  role  = aws_iam_role.db[0].name
}

# ── 데이터 볼륨 — 인스턴스와 수명 분리 ───────────────────────────────────
# tfsec:ignore:aws-ebs-encryption-customer-key — KMS CMK 는 월 $1+. AWS 관리 키 암호화로 충분(ecr.tf·S3 state 와 같은 판단)
resource "aws_ebs_volume" "db_data" {
  count             = local.db_count
  availability_zone = local.db_az
  size              = var.db_data_volume_gb
  type              = "gp3"
  encrypted         = true

  tags = { Name = "${var.cluster_name}-prod-db-data", Role = "prod-db" }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_instance" "db" {
  count                  = local.db_count
  ami                    = data.aws_ami.ubuntu_arm.id
  instance_type          = var.db_instance_type
  subnet_id              = local.db_subnet_id
  vpc_security_group_ids = [aws_security_group.db[0].id]
  iam_instance_profile   = aws_iam_instance_profile.db[0].name

  # T4g 기본은 `unlimited` — 버스트 초과분이 크레딧 산술 밖으로 샌다(B5-24).
  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_tokens   = "required" # IMDSv2
    http_endpoint = "enabled"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 8
    encrypted             = true
    delete_on_termination = true # OS·설정만. 데이터는 위 볼륨.
  }

  # 🔴 비밀을 넣지 마라 — PR plan 역할이 state·DescribeInstanceAttribute 로 읽는다(B5-18④).
  user_data = templatefile("${path.module}/db-userdata.sh.tftpl", {
    region     = var.region
    ssm_prefix = var.db_ssm_prefix
    pg_major   = var.db_postgres_major
    vpc_cidr   = var.vpc_cidr
    data_vol   = replace(aws_ebs_volume.db_data[0].id, "-", "")
  })

  tags = { Name = "${var.cluster_name}-prod-db", Role = "prod-db" }

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [ami, user_data]
  }
}

resource "aws_volume_attachment" "db_data" {
  count       = local.db_count
  device_name = "/dev/sdf"
  volume_id   = aws_ebs_volume.db_data[0].id
  instance_id = aws_instance.db[0].id
}
