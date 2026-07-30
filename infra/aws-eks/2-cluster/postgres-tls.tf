# ── ⑬ in-cluster Postgres TLS (Stage 3a) ─────────────────────────
#
# ★ **이 파일이 존재하는 이유가 Stage 3의 핵심 발견이다.**
#
# "앱 코드 변경 0으로 RDS ↔ in-cluster를 갈아끼운다"가 Stage 3의 전제였다.
# 실제로 해보니 **한 군데서 깨졌다** (07-30 실측):
#
#   Caused by: org.postgresql.util.PSQLException: The server does not support SSL.
#
# `application-prod.yml`의 jdbc-url이 `?sslmode=require`로 **하드코딩**돼 있다.
# RDS는 TLS를 켜 놓은 채로 오기 때문에 이게 그냥 통했다. 맨 postgres 이미지는 아니다.
#
# 🔑 **관리형이 "공짜로 주던 것"의 목록에 TLS가 있었다는 걸 여기서 알게 된다.**
#    자동 백업·PITR·로테이션처럼 눈에 띄는 것 말고, **아무도 언급하지 않는 기본값**.
#    자체운영으로 옮긴다는 건 이런 것까지 내가 소유한다는 뜻이다.
#
# ── 왜 initContainer에서 openssl로 만들지 않나 ──
#   ① `postgres:17-alpine`에 openssl이 없다(실측: `sh: openssl: not found`).
#      다른 이미지를 끌어오면 검증 안 된 서드파티 의존이 하나 는다.
#   ② 손으로 `kubectl create secret`을 만들지 않는다는 Stage 2 규칙과 충돌한다.
#   ③ tofu가 만들면 **Stage 2에서 세운 Secrets Manager → ESO 파이프를 그대로 재사용**한다.
#      새 개념이 하나도 안 늘고, 세션마다 자동 재생성된다.

# 자기서명 인증서로 충분한 이유:
#   `sslmode=require`는 **암호화만 요구**하고 인증서 검증은 하지 않는다.
#   검증까지 하려면 `verify-ca`/`verify-full`이어야 하고, 그때는 CA를 클라이언트에
#   심어야 한다. 학습 클러스터에서 우리가 원하는 건 "전송 구간 암호화"이므로 여기까지가 맞다.
#   ⚠️ 이건 **MITM을 막지 못한다.** 실제 운영이라면 cert-manager로 CA 체계를 세운다.
resource "tls_private_key" "postgres" {
  count     = local.incluster_enabled
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "postgres" {
  count           = local.incluster_enabled
  private_key_pem = tls_private_key.postgres[0].private_key_pem

  subject {
    common_name  = "postgres"
    organization = "devquest-eks-learning"
  }

  # 클러스터 안에서 이 DB를 부르는 이름들. sslmode=require면 검증을 안 하므로
  # 엄밀히는 불필요하지만, 나중에 verify-full로 올릴 때 필요한 형태를 미리 갖춰 둔다.
  dns_names = [
    "postgres",
    "postgres.default.svc.cluster.local",
    "postgres-0.postgres.default.svc.cluster.local",
  ]

  # 세션마다 재생성되므로 길 이유가 없다. 1년은 넉넉한 상한.
  validity_period_hours = 8760

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "server_auth",
  ]
}

# 인증서·키를 Secrets Manager에 넣어 ESO가 K8s Secret으로 옮기게 한다.
# tfsec AVD-AWS-0098(LOW): CMK 대신 기본 키 — secrets.tf와 동일 근거(세션마다 폐기되는 학습 자산).
#tfsec:ignore:AVD-AWS-0098
resource "aws_secretsmanager_secret" "postgres_tls" {
  count       = local.incluster_enabled
  name        = "${var.cluster_name}/postgres-tls"
  description = "in-cluster Postgres 자기서명 TLS 인증서/키 (학습 전용, 세션마다 재생성)"

  # 0 = 즉시 삭제. 기본 30일이면 destroy 후에도 이름이 점유돼 다음 apply가 실패한다(secrets.tf 참조).
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "postgres_tls" {
  count     = local.incluster_enabled
  secret_id = aws_secretsmanager_secret.postgres_tls[0].id
  secret_string = jsonencode({
    "server.crt" = tls_self_signed_cert.postgres[0].cert_pem
    "server.key" = tls_private_key.postgres[0].private_key_pem
  })
}
