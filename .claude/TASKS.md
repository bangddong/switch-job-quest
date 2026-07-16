# 미완료 작업

> **IaC-first 전환(07-16)으로 기존 콘솔 작업 TASK-4/5 폐기.** 크레딧 제외 필터·이상탐지는 이제
> 콘솔이 아니라 `0-bootstrap`의 코드(`aws_budgets_budget cost_types` / `aws_ce_anomaly_monitor`)로 처리.
> 콘솔 예산은 활성 가드레일로 유지하다 0-bootstrap apply 후 import/재생성으로 승격.

### TASK-4: 0-bootstrap 착수 준비 — AWS 자격증명 (사용자, 2026-07-16)

IaC-first의 최초 로컬 `tofu apply`(S3 backend 버킷·예산 생성)에는 AWS 자격증명이 필요.
Claude는 AWS 콘솔·자격증명 설정을 대신 못 하므로 사용자가 준비:

1. 부트스트랩용 자격 마련 — 택1:
   - **AWS IAM Identity Center(SSO)** 로 관리자 권한 → `aws configure sso` (권장, 키 없음)
   - 또는 관리자 IAM 사용자 **액세스키** → `aws configure`
   - ⚠️ 액세스키는 로컬에만, **git 절대 금지**. 부트스트랩 후 GitHub OIDC로 전환하고 키 폐기.
2. `aws sts get-caller-identity` 로 계정 확인되면 Claude에 알리기 → `0-bootstrap` 코드 작성 착수

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
