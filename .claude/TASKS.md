# 미완료 작업

### TASK-5: 예산에 크레딧 제외 필터 추가 — 계정 생성 +24h 이후 (2026-07-16 등록)

신규 계정이라 Cost Explorer 데이터 미수집으로 charge type 필터를 지금 못 걸었음(일지 07-16 `[막힘]`).
데이터 수집(계정 생성 ~24h, **2026-07-17 이후**) 후 예산 편집으로 추가:

1. Billing → Budgets → `eks-credit-guard` → **Edit**
2. Budget scope → **Filter specific AWS cost dimensions**
3. Dimension **Charge type** / 연산자 **Excludes** / Values에서 **Credit, Refund** 선택 → Apply filter
4. Save. → 완료 후 Claude에 알리면 일지 `[해결]` 기록 + 이 항목 제거
   - 목적: 크레딧이 청구액을 $0으로 가려 알림이 늦는 것 방지 (실사용 비용 기준 경보)

### TASK-4: Cost Anomaly Detection 모니터 생성 — Stage 0 착수 전 (2026-07-16)

예산(`eks-credit-guard`)은 생성 완료. **이상 탐지 모니터가 남음.**

1. AWS 콘솔 → Billing and Cost Management → 좌측 **Cost Anomaly Detection** → Create monitor
2. Monitor type **AWS services**(전체), 이름 예 `all-services`
3. Alert subscription: **Daily summary**, 임계값 예 **$5**, 수신 이메일 등록 → Create
4. 완료 후 Claude에 알리기 → 일지·튜토리얼 0-2 기록 + 이 항목 제거

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
