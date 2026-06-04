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

## 알아둬야 할 비자명적 결정 (추가)

### Spring Boot 4.x Flyway 수동 구성
Spring Boot 4.x에서 Flyway auto-configuration 제거됨 (spring-boot-autoconfigure AutoConfiguration.imports에 없음).
`db-core` 모듈에 `FlywayConfig.kt`로 수동 구성:
- `@Profile("prod")`로 prod 전용 활성화
- `flyway.repair()` → `flyway.migrate()` 순차 호출 (flyway 11.x에서 repairOnMigrate API 제거됨)
- `EntityManagerFactoryDependsOnPostProcessor("flyway")`로 JPA가 Flyway 완료 후 초기화되도록 설정
- 패키지: `org.springframework.boot.jpa.autoconfigure` (Spring Boot 4.x 변경)

## 현재 상태

| 항목 | 내용 |
|------|------|
| 브랜치 | `main` |
| 열린 PR | 없음 |





## 최근 완료 (최근 3건)

| PR/커밋 | 내용 | 날짜 |
|---------|------|------|
| #165 | 모바일 코딩 에디터 CodeMirror 6 교체 (문법 강조·괄호 자동 닫기·Tab 들여쓰기) + orchestrator 훅 절대경로 수정 | 2026-06-04 |
| #163 | 모바일 코딩 에디터 스크롤·코드 잘림·키보드 미표시 수정 | 2026-06-02 |
| #162 | 메일 HTML 템플릿 개선 + dhbang.co.kr 도메인 인증 + AI 질문 프롬프트 강화 | 2026-06-01 |
| #161 | 데일리 메일 AI 질문 생성 + 발송 이력 중복 방지 | 2026-05-31 |
| #160 | 카테고리별 코딩 풀이 레이더 차트 (SVG, 9축) | 2026-05-31 |
| #158 | 코딩 로드맵 해금 기준 — 고유 문제 수(DISTINCT) 기준으로 수정 | 2026-05-30 |
| #157 | 코딩 문제 로드맵 — 카테고리별 단계적 문제 풀기 (9개 카테고리, 잠금/해금) | 2026-05-29 |
| #156 | 모바일 UX 개선 — AI 폼 다단계화, 성장 차트 탭 네비게이션, 코딩 에디터 최적화 | 2026-05-28 |
| #155 | PR 절차 문서화 — git-strategy.md Copilot 리뷰 처리 절차 추가, orchestrator 9단계 git-strategy 참조로 교체 | 2026-05-27 |
| #154 | orchestrator tools 와일드카드 변경 및 mcpServers 제거 (머지 완료) | 2026-05-27 |
| #153 | .claude 구조 개편 — skills universal/project 분리, pair-programmer 에이전트 추가, orchestrator 브랜치 규칙 명시 | 2026-05-26 |

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
