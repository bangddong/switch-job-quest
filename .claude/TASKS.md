# 미완료 작업

> **IaC-first 전환(07-16)으로 기존 콘솔 작업 TASK-4/5 폐기.** 크레딧 제외 필터·이상탐지는 이제
> 콘솔이 아니라 `0-bootstrap`의 코드(`aws_budgets_budget cost_types` / `aws_ce_anomaly_monitor`)로 처리.
> 콘솔 예산은 활성 가드레일로 유지하다 0-bootstrap apply 후 import/재생성으로 승격.

### TASK-4: 0-bootstrap 착수 준비 — AWS 자격증명 (사용자, 2026-07-16, 진행 중)

> ⚠️ **SSO(IAM Identity Center) 경로 폐기** — Organizations로 켜면 **$200 크레딧 즉시 소멸**(일지 `[막힘]`).
> → **IAM 사용자 액세스키**로 확정 (org 안 만듦 = 크레딧 안전).

최초 로컬 `tofu apply`(S3 backend·예산)에 자격증명 필요. Claude는 시크릿 키를 못 다루므로 사용자가 직접:

1. 콘솔 → IAM → Users → **`bootstrap-admin`** 생성, **AdministratorAccess** attach
2. Security credentials → **Create access key** (use case: CLI)
3. **PC 터미널에서 직접** `aws configure` — key/secret 본인 입력, region **`ap-northeast-2`**, output `json`
   - ⚠️ 키를 Claude에 붙여넣지 말 것. `~/.aws/`에만, **git 절대 금지**.
4. "configure 했어" → Claude가 `aws sts get-caller-identity` 확인 → `0-bootstrap` 코드 착수
   - 부트스트랩 후 GitHub OIDC로 전환하고 **이 액세스키 폐기**

### TASK-6: AWS 크레딧 만료일 캘린더 등록 (사용자, 2026-07-23)

Free Plan 크레딧은 **소진 OR 만료 중 먼저 오는 시점에 계정이 자동 폐쇄**된다(과금이 아니라 폐쇄).
실측 만료일을 캘린더에 박아 놓을 것 — prod는 Fly+Neon이라 무영향이나, EKS 학습 인프라·S3 tfstate·
ECR이 계정과 함께 사라진다(폐쇄 후 90일 content 보관, Paid 업그레이드 시 복구·잔여 크레딧 이월).

- **만료일**: **2027-01-15** (계정 API 실측, 약 25주 후)
- **알림 권장**: 만료 2주 전(2027-01-01) — 상시 데모 계획이 있으면 이때 Paid 업그레이드 판단
- 별개 감시: 크레딧 잔액이 **$30 안전 예비**에 근접하면(현재 $199.81) 세션 빈도 조절

## 완료된 항목

### TASK-3: BE 서버 다운 — PR #231 배포 실패 후 헬스체크 미통과 (2026-07-01, 해결됨)
`https://api.quest.dhbang.co.kr/health` 503 → 완전 타임아웃. Grafana Loki 스냅샷 로그로 원인 확정:
Flyway `V8` 마이그레이션 버전이 `core-api`(V8__company_pipeline.sql)와 `db-core`
(V8__create_tech_question_bank.sql, PR #231 신규) 양쪽에 중복 생성되어 앱 부팅 자체가 실패.
- 수정: PR #233 — `V10__create_tech_question_bank.sql`로 rename, 재배포 성공, `/health` 200 복구 확인
- 재발 방지: `be-ci.yml`에 마이그레이션 버전 중복 검사 CI 린트 추가 (PR 단계 자동 차단)

### TASK-1: BE AI Evaluator 캐시 메트릭 관측가능성 추가 (PR #123)
`CacheMetricsAdvisor` 추가 — 매 AI 평가 호출 후 cache_read_input_tokens / cache_creation_input_tokens INFO 로그 출력.

### TASK-2: Claude Code 세션 프롬프트 구조 최적화 (PR #124)
CONTEXT.md 고정 내용(비자명적 결정, 참조 문서) 상단 배치, 동적 내용(현재 상태, 최근 완료) 하단으로 분리.

### [Observability] Sentry → 포기, Logtail 연동 완료
- **Sentry**: Spring Boot 4.x 미지원으로 포기 (PR #52에서 의존성 제거)
- **Logtail (Better Stack)**: 연동 완료 (fly.io log drain 등록)
