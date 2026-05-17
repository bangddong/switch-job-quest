# 미완료 작업

### TASK-3: API 키 발급 및 환경변수 세팅 (PR #135, #136 머지 후)

**RESEND_API_KEY** (PR #135 — SMTP 데일리 메일)
1. https://resend.com 가입
2. API Keys → Create API Key
3. Fly.io 배포 시: `fly secrets set RESEND_API_KEY=<키>`
4. 로컬: `be/core/core-api/src/main/resources/application-local.yml`에 추가
5. `MAIL_ENABLED=true` 설정해야 실제 발송 활성화

**JUDGE0_API_KEY** (PR #136 — 코딩 퀘스트 채점)
1. https://rapidapi.com/judge0-official/api/judge0-ce 구독 (Basic 무료: 50req/일)
2. RapidAPI 대시보드 → X-RapidAPI-Key 복사
3. Fly.io: `fly secrets set JUDGE0_API_KEY=<키>`
4. 로컬: `application-local.yml`에 추가
5. 키 없으면 mock 모드(채점 항상 통과)로 동작함

---

## 완료된 항목

### TASK-1: BE AI Evaluator 캐시 메트릭 관측가능성 추가 (PR #123)
`CacheMetricsAdvisor` 추가 — 매 AI 평가 호출 후 cache_read_input_tokens / cache_creation_input_tokens INFO 로그 출력.

### TASK-2: Claude Code 세션 프롬프트 구조 최적화 (PR #124)
CONTEXT.md 고정 내용(비자명적 결정, 참조 문서) 상단 배치, 동적 내용(현재 상태, 최근 완료) 하단으로 분리.

### [Observability] Sentry → 포기, Logtail 연동 완료
- **Sentry**: Spring Boot 4.x 미지원으로 포기 (PR #52에서 의존성 제거)
- **Logtail (Better Stack)**: 연동 완료 (fly.io log drain 등록)
