# in-cluster Postgres의 superuser 비밀번호.
#
# ══ 왜 2-cluster가 아니라 여기인가 (2026-08-07 실측으로 확정, 원장 L-14) ══
#
# 원래 이 리소스는 `2-cluster/secrets.tf`에 있었고 주석은 이랬다:
#
#     # 세션마다 새로 생성된다(destroy-after-use라 유지할 이유가 없다).
#
# **Stage 3a까지는 참이었다** — 동적 PVC라 볼륨이 세션과 함께 죽었으니, 비밀번호가
# 매번 바뀌어도 매번 새 DB가 `initdb`로 그 비밀번호를 굽었다. 앞뒤가 맞았다.
#
# **Stage 3b가 영속 EBS를 도입하면서 그 전제가 무너졌는데, 리소스도 주석도 안 따라왔다.**
# 08-07 검증 세션에서 실제로 터진 모습:
#
#     tofu destroy  → state에서 random_password가 사라짐
#     tofu apply    → 새 비밀번호 생성 → Secrets Manager → ESO → 앱
#     그런데 볼륨은? postgres 이미지는 POSTGRES_PASSWORD를 **initdb 때만** 쓴다.
#                   데이터 디렉토리가 이미 있으면 읽지도 않고 옛 해시를 유지한다.
#     결과          → core-api CrashLoopBackOff
#                     FATAL:  password authentication failed for user "devquest"
#
# **데이터는 완벽히 살아남았는데 자격증명만 안 붙었다.**
#
# ── 원칙 ────────────────────────────────────────────────────────────
# **볼륨과 수명이 같아야 하는 것은 볼륨과 같은 레이어에 둔다.**
# D-004가 EBS를 2-cluster가 아닌 0-bootstrap에 둔 것과 같은 논리다. 영속 볼륨을
# 도입하는 순간, **볼륨 안에 구워지는 모든 것**(비밀번호 해시 포함)이 같은 제약을 받는다.
#
# ── 기각한 대안 ─────────────────────────────────────────────────────
# `2-cluster`에 두고 `lifecycle { ignore_changes = all }`:
#   ❌ **못 고친다.** ignore_changes는 *state에 있는 값*과 설정을 비교해 diff를 무시하는
#      것이다. destroy가 state에서 리소스를 지우고 나면 무시할 대상 자체가 없다.
#      $0으로 실증(random provider만 사용):
#        ① apply → DMsI65…   ② 재apply → DMsI65…(유지)
#        ③ destroy → 정상 완료(=prevent_destroy와 다르다)   ④ 재apply → wlo88E…(바뀜)
#
# ℹ️ count를 걸지 않는다. `random_password`는 **AWS 리소스가 아니라 state 항목**이라
#    존재 자체에 비용이 0이다. db_mode=rds일 때 안 쓰일 뿐, 만들어 두는 편이 단순하다.
#    (Secrets Manager에 별도 시크릿을 만드는 안도 검토했으나 $0.40/월이 영구로 붙어서
#     기각했다 — 지금 영속 비용이 EBS $0.91/월뿐이라 44% 증가였다.)
resource "random_password" "postgres_master" {
  # PostgreSQL 비밀번호에 특수문자를 넣지 않는다 — JDBC URL·psql 명령줄·
  # K8s Secret(base64)을 오가며 이스케이프 사고가 나는 것을 원천 차단.
  # 길이 32(엔트로피 ~165bit)면 특수문자 없이도 충분하다.
  length  = 32
  special = false

  # 🔴 이 값이 바뀌면 **영속 볼륨 안의 DB와 어긋난다**(위 설명 참조).
  #    바꿔야 한다면 코드만 고치는 것으로는 부족하고, 세션 중에 DB 쪽도 함께 돌려야 한다:
  #      kubectl exec postgres-0 -- psql -U devquest -d devquest \
  #        -c "ALTER USER devquest PASSWORD '<새 값>';"
  #    (컨테이너 안 로컬 소켓은 trust 인증이라 옛 비밀번호 없이도 된다.)
  lifecycle {
    prevent_destroy = true
  }
}
