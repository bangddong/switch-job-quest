# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 현재 상태 (2026-04-21)

| 항목 | 내용 |
|------|------|
| 브랜치 | `main` (최신) |
| 열린 PR | #87 브라우저 재로드 후 aiResults 복원 (머지 대기), #88 agent 프롬프트 TDD 추가 (머지 대기) |

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| #88 | agent 프롬프트 TDD 워크플로우 추가 — be-feature-builder TDD 규칙, qa-reviewer 테스트 커버리지 섹션, fe-feature-builder JSON.parse 타입 가드 | 2026-04-21 |
| #87 | 브라우저 재로드 후 aiResults 복원 — ProgressResult.QuestDetail aiEvaluationJson 추가, App.tsx fetchProgress 응답에서 setAiResults 복원, ProgressServiceTest 케이스 추가 | 2026-04-21 |
| #85 | 1-BOSS 개발자 클래스 판별 AI 평가 — /ai-check/developer-class 신규, 1-1/1-2 aiEvaluationJson 저장, DeveloperClassResultCard FE 렌더링 | 2026-04-21 |

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

- [ ] PR #87, #88 머지
- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획
- [ ] Issue #86: DeveloperClassEvaluator 단위 테스트 추가 (tech-debt, TDD 방식으로 진행)

## 참조 문서

| 주제 | 문서 |
|------|------|
| Copilot 리뷰 처리 | `.claude/docs/copilot-review.md` |
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |
