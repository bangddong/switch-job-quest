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

### TASK-7: EKS 리퍼(dead man's switch) — 새 머신/클론 시 재설치 (2026-07-25)

`.claude/eks-session/` 마커·하트비트·launchd 잡은 **머신 로컬**이라 gitignore된다. 즉 **이 맥에만
설치돼 있고**, 다른 머신에서 clone하면 리퍼가 없어 "끄는 걸 잊음" 자동 방어가 안 걸린다.

- **현재 맥(dhbangui-MacBook-Neo)**: ✅ 설치·로드 완료(07-25). `launchctl list | grep eks-reaper`로 확인.
- **새 머신/재클론 시 1회 실행**:
  ```bash
  bash infra/aws-eks/reaper/install-reaper.sh
  ```
- 상세: `docs/eks-session-sop.md` §안전장치.

## 완료된 항목

### TASK-8: timezone 배포 후 실측 → **불필요해져 종결 (2026-07-28)**
로컬에 colima+docker를 설치해 **배포 전에 이미지로 직접 실측**해버렸으므로 prod 확인이 필요 없다.
결과: `TZ=Asia/Seoul`만으로 이미 `ZoneId.systemDefault()=Asia/Seoul`(temurin alpine에 tzdata 포함)
→ prod는 #210부터 쭉 KST였고 **L-9은 오진**이었음이 확정. 상세는 원장 L-9/L-10, PR #337.

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

## TASK-9: prod(Neon)의 PostgreSQL 메이저 버전 확인

**왜**: EKS 학습 RDS의 `db_engine_version`을 기본 `17.10`으로 뒀는데, prod(Neon)의 메이저를
모르는 상태다. 메이저가 다르면 Flyway 마이그레이션 12개를 EKS에서 돌려봐도
"prod에서도 된다"는 근거가 약해진다(학습 가치는 유지되나 회귀 검증 가치가 떨어짐).

**하는 법** (Neon 콘솔 또는 psql):
```sql
SELECT version();
```
또는 Neon 대시보드 → 프로젝트 → Settings에서 Postgres 버전 확인.

**결과 반영**: `infra/aws-eks/2-cluster/variables.tf`의 `db_engine_version` 기본값을
prod와 같은 메이저로 맞춘다(예: prod가 16이면 `"16.x"`).
지원 버전 확인: `aws rds describe-db-engine-versions --region ap-northeast-2 --engine postgres`

**07-28 실측 보강 (#339 세션)**: RDS **PostgreSQL 17.10**에 대해 Flyway **12개 마이그레이션이
전부 정상 적용**됐다(`Successfully validated 12 migrations` / `Current version of schema "public": 12`).
즉 17.x에서 스키마가 깨지지 않는 것은 확인됐다. 남은 건 "prod와 **같은** 메이저인가"뿐이고,
다르더라도 **학습 진행에는 지장 없다**(회귀 검증의 강도만 낮아진다). 우선순위 낮음.
