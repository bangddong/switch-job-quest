# 작업 컨텍스트

> Claude가 매 대화 끝에 업데이트하는 파일입니다.
> 새 대화 시작 시 이 파일을 먼저 읽으면 이전 상태를 이어받을 수 있습니다.

## 현재 브랜치 상태

| 브랜치 | 상태 | 설명 |
|--------|------|------|
| `main` | 최신 | 인증/정책 분리 + GitHub OAuth 인증 전체 완료 (2026-04-06 정리) |

## 열린 PR

없음

## 완료된 인증/정책 분리 시리즈 (2026-04-05)

| Sprint | PR | 내용 | 상태 |
|--------|-----|------|------|
| BE Auth | #28 | GitHub OAuth + JWT 인증 (JwtProvider, SecurityConfig) | ✅ 머지 |
| BE 1 | #29 | userId DTO 제거 + @AuthenticationPrincipal + try-catch 정리 | ✅ 머지 |
| BE 2 | #30 | PassCriteriaPolicy + GradePolicy 추출 | ✅ 머지 |
| FE | #31 | GitHub OAuth 로그인 + useAuth + apiClient 리팩토링 | ✅ 머지 |

## 완료된 횡단 관심사 리팩토링 시리즈 (2026-04-04)

| Sprint | PR | 내용 | 상태 |
|--------|-----|------|------|
| 1 | #23 | 4-BOSS 합격 기준 이중 판단 제거 | ✅ 머지 |
| 2 | #24 | XP 정책 중앙화 (QuestXpPolicy) | ✅ 머지 |
| 3+4 | #25 | AI 모델 라우팅 + 감사 로그 이벤트화 | ✅ 머지 |

## 최근 결정 사항

### 인증/정책 분리 완료 (2026-04-05)
- **BE Auth**: `JwtProvider`, `JwtAuthFilter`, `SecurityConfig` — stateless JWT, GitHub OAuth `/api/v1/auth/github`
- **BE Sprint 1**: 16개 DTO에서 userId 제거, `@AuthenticationPrincipal`로 SecurityContext 추출, Controller try-catch 제거
- **BE Sprint 2**: `PassCriteriaPolicy.evaluate()`, `GradePolicy.from()` — passScore/grade 판단 중앙화, `@Value passScore` 제거
- **FE**: `useAuth.ts` + `LoginPage` + `AuthCallback` — JWT localStorage, GitHub OAuth flow 완성

### Controller 테스트 패턴 (2026-04-05)
`standaloneSetup` + `@AuthenticationPrincipal` 조합 시 반드시:
```kotlin
.setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
// + @BeforeEach에 SecurityContextHolder.getContext().authentication 설정
// + @AfterEach에 SecurityContextHolder.clearContext()
```

### 횡단 관심사 리팩토링 완료 (2026-04-04)
- **Sprint 2**: `QuestXpPolicy` object 추출 — questId별 baseXp 매핑 + 계산 로직 중앙화
- **Sprint 3**: `bossChatClient` 빈 추가 — BOSS Evaluator 4개에 claude-sonnet-4-5 라우팅 (`@Qualifier`)
- **Sprint 4**: `QuestEvaluatedEvent` 도메인 이벤트 + `QuestAuditEventListener` — 감사 로그 분리

### ACT V 5-BOSS 취뽀 달성 화면 (2026-04-03)
- **BE**: `POST /api/v1/ai-check/journey-report` — 전체 여정 AI 감성 회고 내러티브 생성
- **FE**: `FinalBossView` — 합격 신고 입력 → 취뽀 타이틀/통계/AI 내러티브/마지막 한 마디

### UX 포기 방지 시스템 — 전체 완료 (2026-04-02)
- **Sprint 1** (PR #18): E (다음 퀘스트 연결 카드) + F (재도전 코치 + 이전 답변 불러오기)
- **Sprint 2** (PR #19): B (오늘의 미션 배너) + C (퀘스트 브리핑 화면)
- **Sprint 3** (PR #20): D (필드별 작성 가이드 `?` 버튼) + G (복귀 배너, BE lastCompletedAt)
- **Sprint 4** (PR #21): A (온보딩 스토리텔링 5슬라이드 인트로)

## 보안 조치 완료 (2026-04-06)

### 원인
- `application.yml`의 `${VAR:default}` fallback에 GitHub OAuth secret, JWT secret이 하드코딩됨 (PR #28 이후)
- `application-local.yml`이 존재했지만 시크릿 분리 없이 `application.yml`에 직접 작성
- `.gitignore`에 BE profile yml 제외 규칙 없었음

### 조치 내용
- `application.yml` fallback 시크릿 제거 → `${GITHUB_CLIENT_SECRET}` 형태로 변경
- `application-local.yml` git untrack + 로컬 시크릿 이동
- `.gitignore`에 `**/application-local.yml`, `**/application-secret.yml` 추가
- `fe/src/hooks/useAuth.ts` clientId → `import.meta.env.VITE_GITHUB_CLIENT_ID`
- `fe/.env.local` 생성, `.env.example` 업데이트
- BE/FE `CLAUDE.md`에 시크릿 관리 규칙 추가

### ⚠️ 사용자 필수 조치
- GitHub OAuth App secret **즉시 regenerate** 필요 (이미 public repo에 노출됨)
- regenerate 후 `application-local.yml`의 `REPLACE_WITH_NEW_SECRET_AFTER_REGENERATE` 값 교체

## 다음 작업

- [ ] GitHub OAuth secret regenerate (사용자 직접 수행)
- [ ] 다음 기능 기획

## 멀티 에이전트 운영 노하우

- `settings.json` permissions 설정 필수 (Bash, Write, Edit, Read, Glob, Grep)
- 에이전트는 `isolation: "worktree"` + `run_in_background: true` 조합으로 실행
- **에이전트 PR 리뷰 시 `diff --git` 파일 목록 먼저 확인 → 지시 범위 초과 파일 있으면 즉시 처리**
- 에이전트 완료 후 반드시 기획자(Claude)가 핵심 파일 직접 읽고 리뷰
- 리베이스 충돌 발생 시 Claude가 직접 처리
- **병렬 에이전트 작업 범위가 겹치면 충돌 or 중복 PR 발생** → 에이전트 지시문에 "이 파일만 건드릴 것" 명시

## 환경 메모

- FE Vercel 프로젝트: `fe/.vercel/project.json` (`switch-job-quest`, `team_G2dvpYbZ8iZ0gU18lvcId6zn`)
- BE는 Vercel 미연결 (별도 배포)
- FE 배포: `fe/` 디렉토리 기준으로 `vercel --prod` 실행
