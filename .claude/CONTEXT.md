# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 현재 상태 (2026-04-23)

| 항목 | 내용 |
|------|------|
| 브랜치 | `chore/add-caveman` |
| 열린 PR | 진행 중 — caveman 토큰 절약 모드 |

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| #93 | Windows 훅 수정 + orchestrator 0/9단계 CONTEXT 기록 강화 | 2026-04-23 |
| #92 | agent frontmatter 수정 — model alias, tools format, orchestrator path guard | 2026-04-22 |
| #91 | skills 주입 방식 적용 — be/fe-feature-builder에 skills 필드 추가, Copilot 리뷰 수정 | 2026-04-22 |

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

### 에이전트 Remote Control 운영 방식
- 대화형 세션에서는 named agent(`.claude/agents/*.md`) 스폰 불가 — 내장 타입만 지원
- 오케스트레이터 + remote control 조합: `claude --agent orchestrator --remote-control` (직접 터미널 실행 필요)
- `claude remote-control` 서버 모드는 `--agent` 플래그 미지원 (일반 세션만 생성)

## 다음 작업

- [ ] `claude --agent orchestrator --remote-control` 직접 실행 테스트
- [ ] Issue #86: DeveloperClassEvaluator 단위 테스트 추가 (tech-debt)
- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획

## 참조 문서

| 주제 | 문서 |
|------|------|
| Copilot 리뷰 처리 | `.claude/docs/copilot-review.md` |
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |
