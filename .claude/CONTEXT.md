# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 알아둬야 할 비자명적 결정

### Controller 테스트 패턴
`standaloneSetup` + `@AuthenticationPrincipal` 조합 시 반드시:
```kotlin
.setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
// + @BeforeEach: SecurityContextHolder.getContext().authentication 설정
// + @AfterEach: SecurityContextHolder.clearContext()
```

### 동일 파일 수정 스프린트 — 직렬 순서 필수
두 스프린트가 같은 파일을 수정하면 병렬 브랜치 금지.
앞 PR 머지 완료 후 다음 브랜치 생성. (BE↔FE 다른 파일이면 병렬 OK)

### Observability 최종 상태
- Sentry: Spring Boot 4.x 미지원으로 포기 (PR #52)
- Logtail (Better Stack): fly.io log drain 방식 → 커스텀 LogtailHttpAppender로 직접 전송으로 전환 (feat/logtail-http-appender)
  - `LOGTAIL_SOURCE_TOKEN` fly secret으로 주입, 빈값이면 자동 비활성화
  - `java.net.http.HttpClient` 사용, 내부 큐(max 1000) + `AtomicInteger` 카운터, `ScheduledExecutorService` 1초 배치 플러시

### 에이전트 Remote Control 운영 방식
- 대화형 세션에서는 named agent(`.claude/agents/*.md`) 스폰 불가 — 내장 타입만 지원
- 오케스트레이터 + remote control 조합: `claude --agent orchestrator --remote-control` (직접 터미널 실행 필요)
- `claude remote-control` 서버 모드는 `--agent` 플래그 미지원 (일반 세션만 생성)

## 참조 문서

| 주제 | 문서 |
|------|------|
| Copilot 리뷰 처리 | `.claude/docs/copilot-review.md` |
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |

---

## 현재 상태

| 항목 | 내용 |
|------|------|
| 브랜치 | `fix/fly-grace-period` |
| 열린 PR | #139 — Fly.io grace_period 60s→180s (머지 대기) |

## 최근 완료 (최근 3건)

| PR/커밋 | 내용 | 날짜 |
|---------|------|------|
| #138 | CoreDataSource @Primary 추가 — Flyway 자동설정 DataSource 인식 누락 수정 | 2026-05-19 |
| #137 | Flyway V2 마이그레이션 충돌 수정 (coding_quest V2→V3 rename) | 2026-05-18 |
| #136 | 코딩 문제 퀘스트 (AI 생성 + Judge0 채점 + 코드 에디터) | 2026-05-17 |
| #135 | 데일리 기술 연습 인프라 (이메일 저장 + SMTP + 기술면접 반복) | 2026-05-16 |

## 다음 작업

### 코드 작업
- [ ] client-ai Jackson 2/3 혼재 정리 (CompanyFitEvaluator, MockInterviewEvaluator → tools.jackson 마이그레이션)
- [ ] 에이전트 Disambiguation Gate / Closing Summary 미비점 보완 (Gate 횟수 상한, 트리거 기준 명시 — 실사용 경험 더 쌓은 뒤 결정)

### 사용자 확인 필요
- [x] **RESEND_API_KEY** 발급 완료 → application-local.yml 기입, Fly.io secret 세팅 완료
- [x] **JUDGE0_API_KEY** 발급 완료 → application-local.yml 기입, Fly.io secret 세팅 완료
- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획

### 백로그
- [ ] **Spring 시작 시간 최적화** — 현재 cold start 시 2~3분 소요, 사용자 503 경험
  - 원인: 512MB shared CPU + Neon DB cold start + Flyway 실행 겹침
  - 방향 1: `spring.main.lazy-initialization=true` (필요한 빈만 즉시 초기화)
  - 방향 2: `min_machines_running = 1` (machine 항상 warm, Fly.io 비용 발생)
  - 방향 3: Neon connection pooler (PgBouncer) 활성화 → DB cold start 제거
  - 방향 4: LogtailHttpAppender 초기화 비동기화 (현재 startup 중 60s timeout 블로킹)
- [x] devquest-log-shipper 제거 — 커스텀 Logback HTTP 어펜더 구현 완료
- [x] Disambiguation Gate + Closing Summary 에이전트 패턴 도입 (#127)
