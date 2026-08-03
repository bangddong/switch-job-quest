# ── ⑨ Secrets Manager — 앱이 필요로 하는 값들 ────────────────────
#
# core-api가 prod 프로파일에서 **기본값 없이** 요구하는 환경변수는 7개다.
#
#   DB 계열 4 : DB_HOST DB_NAME DB_USERNAME DB_PASSWORD
#   앱  계열 3 : JWT_SECRET GITHUB_CLIENT_ID GITHUB_CLIENT_SECRET
#
# 관측 계열 3(GRAFANA_LOKI_URL·GRAFANA_LOKI_INSTANCE_ID·GRAFANA_API_KEY)은 **전부 선택**이다.
# 없으면 해당 관측 경로만 꺼지고 앱은 뜬다. 관측 설정 하나가 빠졌다고 앱 전체가 못 뜨는 것은
# 결함이지 운영 요건이 아니다.
#
# ⚠️ 이 숫자는 두 번 틀렸다. 세는 방법이 매번 새 구멍을 놓쳤다:
#   Stage 1 "7개"  → logback이 `source="VAR"` 형식이라 대문자 `${...}` grep에 안 걸렸다(3개 누락).
#   그 뒤 "10개"   → 로깅 3개를 "여기서 주입하지 않는다"고 **적어놓고 아래에서 주입하고 있었다.**
#                    같은 파일 안에서 서술과 코드가 어긋난 채로 유지됐다.
#   → 다시 셀 일이 생기면 문서가 아니라 코드에서 센다:
#        grep -rn '\${[A-Z_]*}' be/core/core-api/src/main/resources/application*.yml
#        grep -rn 'source="' be/support/logging/src/main/resources/logback-spring.xml
#        grep -rn '@Value' be/support be/core --include=*.kt | grep -v ':}'   # 기본값 없는 것만
#
# ── 시크릿을 왜 2개로 나누나 ──
# 소유자와 수명이 다르기 때문이다:
#   core-api-db  → **AWS가 소유**(RDS 관리형 크리덴셜) + tofu가 만든 접속 좌표
#   core-api-app → **내가 소유**(JWT·OAuth)
# 한 덩어리로 묶으면 AWS가 자동 로테이션하는 값과 수동 값이 섞이고,
# Stage 3에서 RDS를 in-cluster Postgres로 갈아낄 때 db쪽만 교체하는 것도 불가능해진다.
# ✅ **07-30 Stage 3a에서 실제로 그렇게 됐다** — db쪽 secret_version만 모드별로 갈라졌고
#    `core-api-app`과 `k8s/base/core-api.yaml`은 한 글자도 안 바뀌었다. 분리의 배당금.

# ── DB 접속 좌표 (host/dbname) ─────────────────────────────────
#
# 왜 별도 시크릿이 필요한가:
#   manage_master_user_password가 만드는 RDS 관리형 시크릿에는
#   **username과 password밖에 없다.** DB_HOST·DB_NAME이 없어서
#   ExternalSecret 하나로 4개 키를 다 채울 수 없다.
#
# host에 `.address`를 쓴다(`.endpoint`가 아니라):
#   .endpoint는 "host:5432" 형태인데, application-prod.yml의 jdbc-url이
#   `jdbc:postgresql://${DB_HOST}/${DB_NAME}` 로 **포트를 포함하지 않는다.**
#   .endpoint를 넣으면 "host:5432/dbname"이 되어 JDBC URL이 깨진다.
# tfsec AVD-AWS-0098(LOW): 고객관리형 KMS 키(CMK) 대신 기본 aws/secretsmanager 키를 쓴다.
# 근거: CMK는 키당 $1/월 고정비인데, 이 시크릿들은 세션마다 만들고 부수는 학습용이고
#   담긴 값도 학습 전용(생성된 JWT 키 + OAuth 자리표시)이다. 보호 대상 가치 < 상시 비용.
#tfsec:ignore:AVD-AWS-0098
resource "aws_secretsmanager_secret" "db_connection" {
  name        = "${var.cluster_name}/db-connection"
  description = "core-api DB 접속 좌표 (host/dbname). 크리덴셜은 RDS 관리형 시크릿에 별도 존재."

  # 🔴 0 = 즉시 삭제. 기본값(30일)이면 destroy 후에도 "삭제 대기" 상태로 남아
  #    시크릿당 $0.40/월이 계속 과금된다 — destroy-after-use에서 놓치기 쉬운 고아.
  #    게다가 이름이 점유된 채라 다음 세션 apply가 InvalidRequestException으로 실패한다.
  recovery_window_in_days = 0
}

# ── 모드별로 담기는 내용이 다르다 ──────────────────────────────
#
# 하나의 리소스에 조건식을 쓰지 않고 **리소스를 둘로 나눈** 이유:
#   ① Terraform의 삼항 연산자는 양쪽 타입을 통일하려 하는데, 두 모드의 키 집합이
#      달라서(2개 vs 4개) `inconsistent types` 에러가 난다.
#   ② 나눠 놓으면 "두 모드가 배타적이고 내용도 다르다"가 코드에서 바로 보인다.
# 같은 secret_id를 가리키므로 실제로 존재하는 시크릿은 언제나 1개다(count가 배타적이라).

# [Stage 2] RDS 모드 — 접속 좌표만. 크리덴셜은 RDS 관리형 시크릿에 따로 있다.
resource "aws_secretsmanager_secret_version" "db_connection_rds" {
  count     = local.rds_enabled
  secret_id = aws_secretsmanager_secret.db_connection.id
  secret_string = jsonencode({
    host   = aws_db_instance.main[0].address
    dbname = aws_db_instance.main[0].db_name
  })
}

# [Stage 3a] in-cluster 모드 — 좌표 + 크리덴셜 **4개 전부**.
#
# 🔑 Stage 2와의 결정적 차이: **시크릿 출처가 하나로 합쳐진다.**
#   Stage 2는 username/password를 AWS(RDS)가 소유해서 ExternalSecret이 두 군데를
#   가리켜야 했고, 그 이름(`rds!db-<uuid>`)을 AWS가 정하는 바람에 apply 후
#   PLACEHOLDER를 sed로 치환하는 절차가 필요했다.
#   in-cluster에선 우리가 비밀번호를 만드므로 그 절차가 통째로 사라진다.
#   → "관리형이 편한 대신 이름을 못 정한다"는 트레이드오프를 양쪽에서 겪게 된다.
#
# host가 `postgres`인 이유: 같은 네임스페이스(default)의 Service 이름은 그대로
#   DNS 이름이 된다. FQDN(postgres.default.svc.cluster.local)을 써도 되지만
#   짧은 이름이 CoreDNS의 search domain으로 해석되므로 동일하게 동작한다.
resource "aws_secretsmanager_secret_version" "db_connection_incluster" {
  count     = local.incluster_enabled
  secret_id = aws_secretsmanager_secret.db_connection.id
  secret_string = jsonencode({
    host     = "postgres"
    dbname   = var.db_name
    username = var.db_master_username
    password = random_password.postgres[0].result
  })
}

# in-cluster Postgres의 superuser 비밀번호.
# RDS의 manage_master_user_password와 같은 역할을 우리가 대신 한다 —
# 즉 **"시크릿을 만드는 주체가 AWS"였던 Stage 2의 반대편**이다.
# 세션마다 새로 생성된다(destroy-after-use라 유지할 이유가 없다).
resource "random_password" "postgres" {
  count = local.incluster_enabled
  # PostgreSQL 비밀번호에 특수문자를 넣지 않는다 — JDBC URL·psql 명령줄·
  # K8s Secret(base64)을 오가며 이스케이프 사고가 나는 것을 원천 차단.
  # 길이 32(엔트로피 ~165bit)면 특수문자 없이도 충분하다.
  length  = 32
  special = false
}

# ── 앱 시크릿 (JWT / GitHub OAuth) ─────────────────────────────
#
# 🔴 **prod의 실제 값을 쓰지 않는다 — 의도적이다.**
#   학습 클러스터에 prod GitHub OAuth 크리덴셜을 넣으면 그 클러스터가
#   실서비스 사용자에게 유효한 토큰을 발급할 수 있게 된다. 학습장에 프로덕션
#   신원 발급 권한을 주는 셈이라 금지.
#
# JWT_SECRET은 tofu가 매 세션 새로 생성한다(random_password). 학습 클러스터의
# 토큰은 그 세션 안에서만 유효하면 충분하다.
# GitHub OAuth 2개는 **자리표시 값**이다. Stage 2의 목표는 "앱이 완전히 부팅되는가"
# (=/health 200)이지 로그인 e2e가 아니다. 이 값들은 빈 생성만 통과시키면 된다.
# 실제 로그인 검증이 필요해지면 학습 전용 OAuth App을 새로 발급해 주입한다.
resource "random_password" "jwt_secret" {
  length  = 64
  special = false # base64/HMAC 키로 쓰이므로 특수문자 불필요, 셸 이스케이프 사고도 예방
}

# tfsec AVD-AWS-0098(LOW): 고객관리형 KMS 키(CMK) 대신 기본 aws/secretsmanager 키를 쓴다.
# 근거: CMK는 키당 $1/월 고정비인데, 이 시크릿들은 세션마다 만들고 부수는 학습용이고
#   담긴 값도 학습 전용(생성된 JWT 키 + OAuth 자리표시)이다. 보호 대상 가치 < 상시 비용.
#tfsec:ignore:AVD-AWS-0098
resource "aws_secretsmanager_secret" "app" {
  name        = "${var.cluster_name}/app"
  description = "core-api 앱 시크릿 (JWT·GitHub OAuth). 학습 전용 값 — prod 값 아님."

  recovery_window_in_days = 0 # 위와 동일 근거
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    JWT_SECRET           = random_password.jwt_secret.result
    GITHUB_CLIENT_ID     = var.github_client_id_placeholder
    GITHUB_CLIENT_SECRET = var.github_client_secret_placeholder

    # ── 관측(Grafana) 3종은 **의도적으로 넣지 않는다** ──
    #
    # 종전엔 더미 값 3개를 주입했다. ~~"자리표시니까 무해하다"~~ — 틀렸다.
    # 자리표시가 **비어있지 않다**는 사실 자체가 두 관측 경로를 모두 켜버렸다:
    #
    #   GRAFANA_API_KEY = "learning-placeholder-not-a-real-api-key"
    #     + application-prod.yml의 instance-id "1680166"(**진짜 값**, 하드코딩)
    #     → 학습 클러스터가 60초마다 우리 **실제 Grafana Cloud 스택**에
    #       틀린 자격증명으로 인증을 시도했다. "실 크리덴셜을 학습 환경에 두지 않는다"는
    #       원칙을 키에만 지키고 인스턴스 ID로 새어나간 것이다.
    #   GRAFANA_LOKI_URL = "http://127.0.0.1:3100/..."
    #     → logback의 `<if property("grafanaLokiUrl").length() > 0>`가 참이 되어
    #       LOKI 어펜더가 붙고, 아무도 안 듣는 포트로 계속 전송을 시도했다.
    #
    # 🔑 **값이 없는 것이 곧 스위치다.** 두 경로 모두 부재를 기본-꺼짐으로 해석한다:
    #     logback  → `defaultValue=""` + `<if>`로 어펜더 정의 자체를 건너뜀 (5cf76da)
    #     메트릭   → `${GRAFANA_API_KEY:}` + Condition으로 빈 미등록 + WARN 1줄
    #   (영속 EBS에서 "태그를 안 다는 것이 삭제 잠금"이었던 것과 같은 모양이다.)
    #
    # 🔴 **전제: ECR 이미지가 이 커밋 이후여야 한다.**
    #    구버전 이미지는 GRAFANA_API_KEY에 기본값이 없어(`@Value("${GRAFANA_API_KEY}")`)
    #    **부팅 자체가 실패**한다 — `PlaceholderResolutionException`.
    #    다음 세션 전에 `ECR Push` 워크플로를 한 번 돌릴 것. (TASKS.md 등재)
  })
}
