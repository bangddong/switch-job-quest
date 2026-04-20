# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 현재 상태 (2026-04-20)

| 항목 | 내용 |
|------|------|
| 브랜치 | `main` (최신) |
| 열린 PR | 없음 |

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| #82 | Copilot gate 자동 트리거 수정 — pull_request_review: submitted 트리거 추가로 Copilot 리뷰 후 자동 failure 전환 | 2026-04-20 |
| #80 | FE AI 분석 로딩 박스 UI 통일 — MockInterviewPanel/CoachOnboarding/CoachQASession에 로딩 박스 추가, CoachQASession 에러 핸들링 + textarea disabled 버그 수정 | 2026-04-20 |
| #79 | BOSS 모델 claude-sonnet-4-5 → 4-6 업그레이드, AiClientConfig @Value 기본값 수정, README max-tokens 4000 정합 | 2026-04-19 |
| #77 | FE growth dashboard — timezone 파싱 수정, 모바일 그리드 레이아웃, HourlyActivity O(N) 최적화 | 2026-04-17 |

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
- Logtail (Better Stack): fly.io log drain 연동 완료

## 다음 작업

- [ ] LoadingBox 공용 컴포넌트 추출 (#81 — refactor)
- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획

## 참조 문서

| 주제 | 문서 |
|------|------|
| Copilot 리뷰 처리 | `.claude/docs/copilot-review.md` |
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |
