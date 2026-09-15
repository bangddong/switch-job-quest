# ACM 퍼블릭 인증서 — 학습 클러스터 ALB 의 HTTPS 리스너용 (선행 조건 3).
#
# ══ 왜 0-bootstrap 인가 (D-004·L-14 규칙의 다섯 번째 적용) ══════════════
#
# 규칙: **수명이 같아야 하는 것은 같은 레이어에 둔다.**
# 앞선 네 번 — ①postgres 비밀번호 ②영속 EBS ③백업 S3 ④JWT 서명 키.
#
# 다섯 번째가 이것이다. **인증서의 수명은 클러스터가 아니라 도메인에 묶인다.**
# 검증이 Cloudflare 수동 CNAME 이라, 2-cluster 에 두면 세션마다 destroy 되고
# 세션마다 사람이 손으로 DNS 를 다시 넣어야 한다. 그건 수명 불일치다.
#
# ══ 🔴 그런데 앞선 네 번과 다른 점이 둘 있다 — 규칙을 복사하면 틀린다 ══
#
# ── 차이 ①: `prevent_destroy` 를 **붙이지 않는다** ──────────────────
#
# 앞의 래치 3종(`random_password` ×2, `aws_ebs_volume`)은 **강제 재생성 트리거가
# 현실적으로 없다.** ACM 은 다르다 — `domain_name` 과 `subject_alternative_names` 가
# 바뀌면 provider 가 **replacement 를 강제**한다. 붙이면 이렇게 깨진다:
#
#     prod 전환 때 api.quest.dhbang.co.kr 를 SAN 에 추가
#       → replacement 필요 → `Instance cannot be destroyed`
#       → infra-deploy.yml 이 **매 머지마다 빨개진다**
#       → 풀려면 lifecycle 블록 제거 PR 이 먼저 머지돼야 한다
#
# 그리고 보호 대상의 성격 자체가 다르다. 래치의 정당화는 **"잃으면 무엇을 잃나"** 인데
# (EBS = 6개월치 데이터, JWT = 전 사용자 강제 로그아웃), 퍼블릭 ACM 인증서는
# **재발급이 $0** 이고 잃는 것은 *사용자의 Cloudflare 작업 2분*뿐이다.
#
# 🔑 **D-004 는 「레이어 선택」을 정당화하지 「래치」를 정당화하지 않는다.**
#    두 결정이 앞선 네 번에서 늘 같이 왔기 때문에 한 덩어리로 보이지만 별개다.
#
# ── 차이 ②: `for_each` 환경 축을 **복사하지 않는다** ────────────────
#
# `jwt-secret.tf` 가 두 환경을 미리 만드는 근거는 이 문장이다:
#
#     ℹ️ `random_password` 는 AWS 리소스가 아니라 **state 항목**이라 존재 자체가 $0 이다.
#        prod 키를 미리 만들어 둬도 과금되지 않고, 어디에도 주입되지 않는다
#
# **이 전제가 ACM 에는 전이되지 않는다 — ACM 인증서는 실제 AWS 리소스다.**
# `for_each = var.jwt_environments` 로 돌리면 `prod` 엔트리가 api.quest.dhbang.co.kr
# 인증서를 만드는데, 우리는 **prod DNS 를 건드리지 않기로 했다**(학습 서브도메인 결정).
# → 검증 CNAME 을 넣을 수 없음 → **72시간 뒤 VALIDATION_TIMED_OUT**
# → 죽은 인증서가 영속 레이어에 박히고 PERSISTENT-RESOURCES 등재 대상까지 된다.
#
# 환경 축은 prod 전환 때 **SAN 추가 또는 별도 리소스**로 연다. 지금은 단일 도메인이다.
#
# ══ 🔴 `aws_acm_certificate_validation` 을 넣지 않는 이유 ═══════════════
#
# `infra-deploy.yml` 이 main 머지 시 `layer: [0-bootstrap, 1-network]` 를
# `tofu apply -auto-approve` 한다. `aws_acm_certificate_validation` 은 CNAME 이
# 전파될 때까지 **블로킹**하므로, CI 가 사람의 수동 DNS 작업을 기다리다 타임아웃한다.
#
# 대신 검증 레코드를 **출력으로 내보내고**(`acm_domain_validation`, non-sensitive)
# 사람이 Cloudflare 에 넣는다. 절차는 `.claude/TASKS.md` TASK-10.
# 인증서는 그동안 `PENDING_VALIDATION` 으로 존재하며 **비용은 $0** 이다.
#
# ⚠️ **72시간 제한.** 그 안에 CNAME 이 안 들어가면 `VALIDATION_TIMED_OUT` 이 되고
#    되살릴 수 없다(taint 후 재생성해야 한다). TASK-10 에 명시돼 있다.
#
# ══ ⚠️ Cloudflare 는 반드시 **DNS only** ══════════════════════════════
#
# 신규 레코드는 Cloudflare 에서 기본 **Proxied(주황 구름)** 다. 그대로 두면:
#   - 브라우저가 보는 것은 Cloudflare 엣지 인증서이지 **이 인증서가 아니다**
#     → 학습 목표("ALB 의 실물 HTTPS 를 본다")가 **조용히** 달성되지 않는다
#   - 존 SSL 모드가 Flexible 이면 Cloudflare→ALB 가 HTTP:80 인데 ALB 가 301 →
#     **리다이렉트 루프**
#
# 🔴 이 레포는 같은 사고를 이미 겪었다 — `.claude/CONTEXT.archive.md` (2026-04-08):
#     "Cloudflare: api.quest.dhbang.co.kr DNS Proxied → DNS only (SSL 핸드셰이크 실패 해결)"
#
# ══ 비용 ═══════════════════════════════════════════════════════════════
#
# 퍼블릭 ACM 인증서는 **발급·갱신·보관 전부 $0** 이다(사설 CA 만 유료).
# 그래도 PERSISTENT-RESOURCES.md 에 등재한다 — 원장은 금액이 아니라 **존재**를
# 기준으로 삼는다고 스스로 못 박았다(S3 백업 버킷 행과 같은 근거).

resource "aws_acm_certificate" "learning" {
  domain_name       = var.learning_domain_name
  validation_method = "DNS"

  lifecycle {
    # 🔴 `prevent_destroy` 와 **같이 쓰지 마라** — 후자가 이겨 재생성 자체가 막히고,
    #    그 조합은 에러 없이 모순이 된다(위 「차이 ①」).
    #
    # 이것만 두는 이유: 도메인을 바꿀 때 ALB 리스너가 옛 인증서를 참조 중이면
    # 선-삭제가 `ResourceInUseException` 으로 실패한다. 새것을 먼저 만들게 한다.
    create_before_destroy = true
  }

  tags = {
    Name        = "devquest-eks-learning"
    Environment = "learning"
    # 세션과 함께 사라지지 않는다는 표시. EBS 의 Persistent 태그와 같은 의도다.
    Persistent = "true"
  }
}
