# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 현재 상태 (2026-04-16)

| 항목 | 내용 |
|------|------|
| 브랜치 | `main` (최신) |
| 열린 PR | 없음 |

## 최근 완료 (최근 3건)

| PR | 내용 | 날짜 |
|----|------|------|
| #68 | Copilot 게이트 reply 트리거 + `cancel-in-progress: false` | 2026-04-16 |
| #67 | GitHub OAuth `redirect_uri` 누락 수정 (`DEFAULT` 에러 해결) | 2026-04-16 |
| #52 | Sentry 의존성 제거 (Spring Boot 4.x 미지원) | 2026-04-16 |

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

### Copilot Gate 동작 흐름
```
PR 열림 → Commit Status: pending
Copilot 리뷰 → 인라인 코멘트 있으면 → failure
claude-review-responder 답글 → Gate dispatch → success
→ mergeStateStatus: CLEAN → 일반 머지 가능 (--admin 불필요)
```
- Commit Status 방식 (SHA 직접 기록) — check_suite 독립적
- `cancel-in-progress: false` — 취소된 check run이 branch protection을 block하는 현상 방지

### Observability 최종 상태
- Sentry: Spring Boot 4.x 미지원으로 포기 (PR #52)
- Logtail (Better Stack): fly.io log drain 연동 완료

## 다음 작업

- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획

## 참조 문서

| 주제 | 문서 |
|------|------|
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |
