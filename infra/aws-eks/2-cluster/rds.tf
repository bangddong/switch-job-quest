# ── ⑧ RDS PostgreSQL (학습용, destroy-after-use) ────────────────
#
# 왜 여기(2-cluster)에 있나 — **의도적이며 옮기면 안 된다.**
#   dead man's switch 리퍼(.claude/scripts/eks-reaper.sh)는 대상 디렉토리를
#   `infra/aws-eks/2-cluster`로 하드코딩해 destroy한다(eks-session-marker.sh:22).
#   RDS를 별도 레이어(3-data 등)로 빼면 **리퍼 사각지대**가 되어, 사람이 세션을
#   방치했을 때 클러스터는 자동 정리되고 RDS만 살아남아 영구 과금된다.
#   → "레이어 분리가 더 깔끔하다"는 직관보다 **과금 안전장치 사정권**이 우선한다.
#
# 왜 프라이빗 서브넷이 아닌가 (검토 후 기각):
#   ① 1-network는 CI 자동 apply 대상(.github/workflows/infra-deploy.yml)이라
#      머지 즉시 적용되고, 그 레이어는 리퍼가 건드리지 않는다.
#   ② 2-cluster의 data.terraform_remote_state.network는 **이미 apply된** state를 읽으므로,
#      1-network에 출력만 추가하고 apply하지 않으면 `tofu plan`이
#      "This object does not have an attribute named ..."로 실패한다(순서 의존성).
#   RDS 서브넷그룹 요건은 "2개 이상 AZ"뿐이고 이미 2a/2c 퍼블릭 서브넷이 있다.
#   publicly_accessible=false + 보안그룹 제한이면 **인터넷에서 도달 불가**는 동일하게 성립한다.

# 서브넷 그룹 — RDS를 어느 서브넷에 놓을지. 2개 이상 AZ 필수(고가용성 전제 때문).
# 우리는 Single-AZ지만 그래도 2개를 요구한다 — AWS가 장애조치 여지를 강제하는 것.
resource "aws_db_subnet_group" "main" {
  name       = "${var.cluster_name}-db"
  subnet_ids = data.terraform_remote_state.network.outputs.public_subnet_ids

  tags = {
    Name = "${var.cluster_name}-db"
  }
}

# 보안그룹 — "누가 5432에 들어올 수 있나"의 유일한 통제점.
# 소스를 CIDR이 아니라 **클러스터 보안그룹 ID**로 지정한다:
#   VPC CNI에서 파드는 노드 ENI의 IP를 쓰고, 관리형 노드그룹의 노드 ENI에는
#   EKS가 만든 cluster security group이 붙는다. 따라서 이 규칙 하나로
#   "이 클러스터의 파드만 DB 접근 가능"이 정확히 표현된다.
#   CIDR(10.0.0.0/16)로 열면 VPC 안 아무 리소스나 접근 가능해져 의미가 흐려진다.
#
# 🔴 description은 **반드시 ASCII만** (07-28 apply에서 실측한 함정).
#    EC2 보안그룹 description의 허용 문자는 a-zA-Z0-9와 ` ._-:/()#,@[]+=&;{}!$*` 뿐이다.
#    한글을 넣으면 CreateSecurityGroup이 `Client.InvalidParameterValue`로 거부되는데,
#    **tofu validate·plan·tfsec은 전부 통과시킨다** (AWS API를 호출해야만 드러남).
#    게다가 provider가 재시도하느라 에러가 즉시 안 뜨고 몇 분간 "Creating..."으로 멈춰 있어
#    원인이 안 보인다 → CloudTrail lookup-events 원문의 errorCode로 확정했다.
#    ⚠️ 같은 제약이 aws_vpc_security_group_ingress_rule.description에도 적용된다(아래).
#    반면 Secrets Manager·IAM·ECR의 description은 한글을 받는다 — "AWS 전체"가 아니라 EC2 계열만.
resource "aws_security_group" "rds" {
  name        = "${var.cluster_name}-rds"
  description = "RDS PostgreSQL - access allowed only from EKS cluster pods"
  vpc_id      = data.terraform_remote_state.network.outputs.vpc_id

  tags = {
    Name = "${var.cluster_name}-rds"
  }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_cluster" {
  security_group_id            = aws_security_group.rds.id
  # ASCII만 (위 aws_security_group.rds 주석 참조)
  # 의미: EKS 클러스터 보안그룹(노드 ENI)에서 오는 PostgreSQL 트래픽만 허용
  description                  = "PostgreSQL from EKS cluster security group (node ENI)"
  referenced_security_group_id = aws_eks_cluster.main.vpc_config[0].cluster_security_group_id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

# egress 규칙을 **의도적으로 만들지 않는다.**
# aws_security_group을 인라인 규칙 없이 선언하면 기본 allow-all egress가 제거된다.
# RDS는 아웃바운드 인터넷이 필요 없다(그래서 NAT Gateway도 필요 없다 = $32/mo 회피).

# ── RDS 인스턴스 ───────────────────────────────────────────────
#
# tfsec 근거 ignore — 전부 "학습용 destroy-after-use"라는 전제에서 나온 의도된 선택:
#   performance-insights   : 비용 회피(무료 티어 7일 보존이 있으나 인스턴스 클래스 제약·복잡도 대비 학습가치 없음)
#   backup-retention       : 0으로 둔다. **자동 백업이 남으면 destroy 후에도 과금되는 고아**가 된다.
#                            세션마다 부수는 학습 DB라 백업으로 지킬 데이터 자체가 없다(Flyway가 스키마를 재생성).
#   deletion-protection    : destroy-after-use의 정면 충돌. 켜면 tofu destroy가 실패하고
#                            **리퍼(dead man's switch)까지 무력화**된다 — 안전장치를 지키려면 반드시 false.
#   public-db-access       : publicly_accessible=false로 이미 차단. 퍼블릭 "서브넷"에 있을 뿐
#                            퍼블릭 "액세스"는 아니다(위 서브넷 선택 근거 참조).
#
# ⚠️ ID 표기 주의: tfsec 1.28에서 이 규칙들은 Rego(AVD) 기반이라 레거시 문자열 ID
#   (aws-rds-enable-deletion-protection 등)로 쓰면 **조용히 무시된다** — ignore가 안 먹는데
#   에러도 안 난다. `tfsec --include-ignored --format json`으로 실제 rule_id를 확인하고 적을 것.
#tfsec:ignore:AVD-AWS-0133
#tfsec:ignore:AVD-AWS-0077
#tfsec:ignore:AVD-AWS-0082
resource "aws_db_instance" "main" {
  identifier     = "${var.cluster_name}-db"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_master_username

  # 마스터 비밀번호를 **코드·tfstate 어디에도 두지 않는다.**
  # AWS가 Secrets Manager에 직접 생성하고 소유·로테이션한다.
  # 이게 이번 Stage의 핵심 학습 포인트: "시크릿을 만드는 주체가 AWS"인 구성.
  # 생성된 시크릿 ARN은 aws_db_instance.main.master_user_secret[0].secret_arn 으로 읽는다.
  manage_master_user_password = true

  allocated_storage = 20
  storage_type      = "gp3"
  # 기본 aws/rds 관리형 KMS 키로 암호화(추가 비용 없음).
  # 고객관리형 키(CMK)를 쓰면 $1/월이 붙어 학습 목적에 맞지 않는다.
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  # IAM DB 인증 — tfsec AVD-AWS-0176 지적을 ignore로 덮지 않고 **실제로 켠다.**
  # 추가 비용 0이고, 켜도 기존 비밀번호 인증 경로는 그대로 동작한다(둘은 배타적이지 않다).
  # 지금 앱은 Secrets Manager의 비밀번호로 붙지만, 켜두면 "파드가 IRSA로 받은 IAM 신원으로
  # DB에 직접 인증"하는 확장(= 비밀번호 자체를 없애는 구성)을 나중에 코드 변경만으로 실습할 수 있다.
  iam_database_authentication_enabled = true

  # ── 🔴 destroy-after-use 3종 세트 — 하나라도 빠지면 돈이 샌다 ──
  #
  # skip_final_snapshot: 기본값 false다. false면 destroy 시 최종 스냅샷을 요구하고,
  #   final_snapshot_identifier가 없으면 **에러로 destroy가 실패**한다.
  #   리퍼는 `tofu destroy -auto-approve`를 통째로 실행하므로(eks-reaper.sh:75)
  #   RDS 하나가 실패하면 **EKS 컨트롤플레인·노드그룹까지 살아남는다.**
  #   즉 이 한 줄이 없으면 과금 안전장치 전체가 무력화된다.
  skip_final_snapshot = true
  # 자동 백업 스냅샷은 인스턴스를 지워도 남아 계속 과금된다 → 함께 삭제.
  delete_automated_backups = true
  # 백업 자체를 끈다(0 = 비활성). 위 두 줄과 함께 "지우면 아무것도 안 남는다"를 보장.
  backup_retention_period = 0
  # ⚠️ 이 ignore는 **이 줄 바로 위**에 있어야 한다. AVD-AWS-0177은 블록이 아니라
  #    속성 라인에 지적을 걸어서, 리소스 상단에 둔 ignore는 먹지 않는다(실측).
  #tfsec:ignore:AVD-AWS-0177
  deletion_protection = false

  # 학습용이라 유지보수 창을 기다릴 이유가 없다.
  apply_immediately = true
  # 마이너 자동 업그레이드: 세션마다 새로 만드는 DB라 실질 영향 없으나,
  # engine_version을 명시적으로 핀했으므로 예기치 않은 변경을 막기 위해 끈다.
  auto_minor_version_upgrade = false

  tags = {
    Name    = "${var.cluster_name}-db"
    Purpose = "learning-ephemeral"
  }
}
