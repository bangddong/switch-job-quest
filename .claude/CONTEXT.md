# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 현재 상태 (2026-04-24)

| 항목 | 내용 |
|------|------|
| 브랜치 | `feature/desgin-system` |
| 열린 PR | #100 — PixelIcon 픽셀 아트 디자인 시스템 도입 (머지 대기) |

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| #99 | Copilot gate 레이스 컨디션 수정 + 커밋 컨벤션 한국어 전환 | 2026-04-24 |
| #98 | README 인프라 구성도 추가 — Vercel→Fly.io→외부 API 흐름, CI/CD, 외부 의존성 표 | 2026-04-24 |
| #97 | README 아키텍처 보강 — 모듈 의존성, 로그 파이프라인, 배포 구성 추가 | 2026-04-24 |

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

### Copilot Gate
Copilot 리뷰가 달렸는데 gate가 pending이면 수동 트리거 필요:
`gh workflow run copilot-review-evaluator.yml -f pr_number=<PR>`
→ 상세 처리 절차: `.claude/docs/copilot-review.md`

### Observability 최종 상태
- Sentry: Spring Boot 4.x 미지원으로 포기 (PR #52)
- Logtail (Better Stack): fly.io log drain 방식 → 커스텀 LogtailHttpAppender로 직접 전송으로 전환 (feat/logtail-http-appender)
  - `LOGTAIL_SOURCE_TOKEN` fly secret으로 주입, 빈값이면 자동 비활성화
  - `java.net.http.HttpClient` 사용, 내부 큐(max 1000) + `AtomicInteger` 카운터, `ScheduledExecutorService` 1초 배치 플러시

### 에이전트 Remote Control 운영 방식
- 대화형 세션에서는 named agent(`.claude/agents/*.md`) 스폰 불가 — 내장 타입만 지원
- 오케스트레이터 + remote control 조합: `claude --agent orchestrator --remote-control` (직접 터미널 실행 필요)
- `claude remote-control` 서버 모드는 `--agent` 플래그 미지원 (일반 세션만 생성)

## 다음 작업

### 코드 작업
- [ ] Issue #86: DeveloperClassEvaluator 단위 테스트 추가
- [ ] client-ai Jackson 2/3 혼재 정리 (CompanyFitEvaluator, MockInterviewEvaluator → tools.jackson 마이그레이션)

### 사용자 확인 필요
- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획

### 백로그
- [x] devquest-log-shipper 제거 — 커스텀 Logback HTTP 어펜더 구현 완료 (feat/logtail-http-appender, a6f6d22)
  - fly secret 설정 + fly.io log drain 제거는 PR 머지 후 사용자 직접 실행 필요

## 참조 문서

| 주제 | 문서 |
|------|------|
| Copilot 리뷰 처리 | `.claude/docs/copilot-review.md` |
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |
