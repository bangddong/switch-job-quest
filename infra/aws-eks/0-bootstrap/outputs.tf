output "state_bucket" {
  description = "remote state S3 버킷 (backend.tf에 사용)"
  value       = aws_s3_bucket.tfstate.id
}

output "lock_table" {
  description = "state 락 DynamoDB 테이블 (backend.tf에 사용)"
  value       = aws_dynamodb_table.tflock.name
}

output "account_id" {
  description = "현재 AWS 계정 ID (민감 — 로그 노출 방지)"
  value       = data.aws_caller_identity.current.account_id
  sensitive   = true
}

output "github_actions_role_arn" {
  description = "CI 워크플로가 assume할 역할 ARN (apply-plan 파이프라인에서 사용)"
  value       = aws_iam_role.github_actions.arn
}

output "ecr_repository_urls" {
  description = "앱별 ECR 레포 URL (docker push 대상 · K8s 매니페스트 image: 값)"
  value       = { for name, repo in aws_ecr_repository.app : name => repo.repository_url }
}

# ── Stage 3b: 2-cluster가 remote state로 읽어가는 값 ──────────────
#
# 이 두 출력이 "영속 레이어 ↔ 세션 레이어"를 잇는 유일한 통로다.
# 2-cluster는 이 값으로 ①노드그룹을 같은 AZ에 고정하고 ②PV 매니페스트에 볼륨 ID를 주입한다.

output "persistent_az" {
  description = <<-EOT
    영속 EBS가 있는 AZ. 2-cluster의 노드그룹이 이 AZ로 고정된다.
    🔑 이 값의 단일 출처는 var.persistent_az다 — 2-cluster에 같은 변수를 만들지 말 것.
  EOT
  value       = var.persistent_az
}

output "postgres_data_volume_id" {
  description = <<-EOT
    영속 Postgres 데이터 볼륨 ID (static PV의 volumeHandle에 들어간다).
    postgres_persistent_volume_enabled = false면 null — 그 경우 Stage 3b는 성립하지 않는다.
  EOT
  # count 기반 리소스는 [0] 인덱싱이 count=0에서 에러다. one()은 null을 준다(rds.tf와 같은 패턴).
  value = one(aws_ebs_volume.postgres_data[*].id)
}

output "postgres_master_password" {
  description = <<-EOT
    in-cluster Postgres superuser 비밀번호.

    🔴 **영속 EBS와 수명이 같아야 하므로 여기(0-bootstrap)에서 만든다.**
    2-cluster에 두면 세션마다 destroy되어 재생성되는데, 볼륨 안의 DB는
    initdb 때의 옛 해시를 그대로 들고 있어 로그인이 깨진다(원장 L-14, 08-07 실측).
    상세는 `postgres-password.tf`.
  EOT
  value       = random_password.postgres_master.result
  sensitive   = true
}

output "backup_bucket" {
  description = "DB 논리 백업 버킷 이름 (db-backup.sh / db-restore.sh 가 읽어간다)"
  value       = aws_s3_bucket.db_backups.id
}

output "jwt_secrets" {
  description = <<-EOT
    환경별 JWT 서명 키 (map: 환경명 → 키).

    🔴 **토큰 수명 = 사용자 세션 수명이므로 여기(0-bootstrap)에서 만든다.**
    2-cluster에 두면 세션마다 destroy되어 재생성되는데, 이미 발급된 토큰은
    옛 키로 서명돼 있어 전부 무효가 된다 = 전 사용자 강제 로그아웃
    (D-004·L-14 규칙의 네 번째 적용). 상세는 `jwt-secret.tf`.

    🔴 **환경별로 나뉘어 있는 것이 핵심이다.** 키 하나를 공유하면 학습 클러스터가
    prod 유효 토큰을 발급할 수 있게 된다. 소비 측은 반드시 자기 환경 키만
    인덱싱해야 한다: `...outputs.jwt_secrets[var.environment]`.
  EOT
  value       = { for env, pw in random_password.jwt_secret : env => pw.result }
  sensitive   = true
}

# ── 선행 조건 3: HTTPS ────────────────────────────────────────────
#
# 🔴 **두 출력의 sensitive 판정이 다르다. 의도적이다.**
#   ARN 에는 계정 ID 가 들어간다(`arn:aws:acm:<region>:<account>:certificate/...`)
#   → `account_id` 출력과 같은 기준으로 sensitive.
#   검증 레코드는 **공개 DNS 레코드**이고 계정 ID 가 없다 → non-sensitive 여야 한다.
#   사람이 CI 로그에서 읽어 Cloudflare 에 넣어야 하므로, 여기를 sensitive 로 하면
#   `(sensitive value)` 만 남아 **절차가 성립하지 않는다**.

output "acm_certificate_arn" {
  description = "학습 ALB Ingress 의 certificate-arn annotation 에 들어갈 값 (계정 ID 포함 — 민감)"
  value       = aws_acm_certificate.learning.arn
  sensitive   = true
}

output "acm_domain_validation" {
  description = <<-EOT
    Cloudflare 에 넣을 검증 CNAME (name → value). 절차는 `.claude/TASKS.md` TASK-10.
    ⚠️ Cloudflare 에서 **DNS only(회색 구름)** 로 둘 것 — Proxied 면 검증이 통과해도
       브라우저가 ACM 이 아닌 Cloudflare 엣지 인증서를 보게 된다.
  EOT
  value = {
    for o in aws_acm_certificate.learning.domain_validation_options :
    o.domain_name => {
      name  = o.resource_record_name
      value = o.resource_record_value
      type  = o.resource_record_type
    }
  }
}
