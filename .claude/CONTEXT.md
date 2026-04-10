# 작업 컨텍스트

> Claude가 매 대화 끝에 업데이트하는 파일입니다.
> 새 대화 시작 시 이 파일을 먼저 읽으면 이전 상태를 이어받을 수 있습니다.

## 현재 브랜치 상태

| 브랜치 | 상태 | 설명 |
|--------|------|------|
| `main` | 최신 | 리팩토링 시리즈 PR #37~#40 전체 머지 완료 (2026-04-10) |

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

## 아키텍처 강화 (2026-04-07)

### AI 신뢰성
- `AiCallExecutor` 신규 — `devquest.ai.max-retry: 3` 실제 연결, null/예외 시 자동 재시도, 3회 실패 시 `AiEvaluationException`
- 13개 Evaluator 전체 적용

### AI 프롬프트 분리
- `client-ai/src/main/resources/prompts/*.st` — 16개 Spring AI `PromptTemplate` 파일로 코드와 분리
- `InterviewCoachEvaluator` 3개, `MockInterviewEvaluator` 2개로 메서드별 분리

### Policy 단위 테스트 보강
- GradePolicy: 2 → 11개 (전 구간 `@ParameterizedTest`)
- PassCriteriaPolicy: 6 → 8개 (`evaluateMax` 커스텀 passScore 추가)
- QuestXpPolicy: 7 → 24개 (multiplier 그룹, 고정 XP 전체, 경계값)

### CI/CD 강화 (2026-04-07)
- `copilot-review-evaluator.yml` → `Copilot Review Gate`로 교체 (API 키 불필요)
- Copilot 인라인 코멘트에 답글 없으면 머지 블록 (수용/거부 무관, 답글만 있으면 통과)
- `Copilot Review Gate / check-copilot-review` → main 브랜치 required check 등록

## Feature Dev Team 하네스 구축 완료 (2026-04-06)

### 구성
- `.claude/agents/` — be-developer, fe-developer, qa-reviewer (Team 모드), be-feature-builder, fe-feature-builder, logic-reviewer, convention-reviewer, test-writer (Sub-agent 모드)
- `.claude/skills/feature-dev/SKILL.md` — TeamCreate 오케스트레이터 스킬

### 제약 사항 (테스트로 확인)
- `.claude/agents/` 파일은 `subagent_type`으로 직접 참조 불가
- 팀 에이전트 스폰 시 `subagent_type: "general-purpose"` + `prompt`에 에이전트 역할 내용 포함 필요
- `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` → `~/.claude/settings.json` env에 설정 완료

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
- regenerate 후 `application-local.yml` + Fly.io secrets + GitHub Secrets 값 교체

## GitHub OAuth 로그인 수정 (2026-04-08)

| PR | 내용 | 상태 |
|----|------|------|
| #32 | FE AuthCallback 응답 필드 불일치 수정 (`json.success` → `json.result === 'SUCCESS'`) | ✅ 머지 |
| #33 | Copilot Review Gate 트리거/조건 수정 + Claude 자동 답글 봇 로그인 조건 수정 | ✅ 머지 |

### 인프라 조치 (2026-04-08)
- **Cloudflare**: `api.quest.dhbang.co.kr` DNS 레코드 Proxied → DNS only 전환 (SSL 핸드셰이크 실패 해결)
- **Vercel**: `VITE_GITHUB_CLIENT_ID` 환경변수 추가 후 재배포 (`client_id=undefined` 해결)
- **GitHub OAuth App**: Callback URL `https://quest.dhbang.co.kr/auth/callback` 등록
- **Fly.io**: 머신 stopped 상태 → `auto_start_machines = true` 확인 (정상)
- **브랜치 보호**: Required check 이름 `Copilot Review Gate / check-copilot-review` → `check-copilot-review` 수정

### Copilot Review Gate 최종 동작
- PR 열림/커밋 → 실행, Copilot 리뷰 없으면 **fail** ("Copilot 리뷰 대기 중")
- Copilot 리뷰 제출 → 재평가, 미처리 인라인 코멘트 있으면 fail
- 인라인 코멘트에 답글 달면 → 재평가, 모두 처리 시 pass
- `claude-review-responder`: `contains()` 조건으로 Copilot 봇 감지 수정

## 리팩토링 시리즈 (2026-04-09) — 전체 완료

| Sprint | PR | 내용 | 상태 |
|--------|-----|------|------|
| Sprint 1 (BE) | #37 | QuestProgressRecorder 추출 (saveProgress/saveHistory 분리) | ✅ 머지 |
| Sprint 2 (BE) | #38 | BaseAiEvaluator 추상 클래스 + QuestConstants 상수화 | ✅ 머지 (충돌 해결) |
| Sprint 3 (FE) | #34 | gradeUtils.ts + styles.ts 공통 유틸 추출 | ✅ 머지 |
| Sprint 4 (FE) | #39 | ResultHeader + ResultSection 서브컴포넌트 분리 | ✅ 머지 |
| Sprint 5 (FE) | #40 | useAiCheckForm 훅 + FormField 컴포넌트 분리 | ✅ 머지 |

### PR #37↔#38 충돌 원인 및 재발 방지 (2026-04-10)

**원인:** 두 BE 스프린트가 `AiCheckService.kt` 동일 라인을 다른 방향으로 수정
- #37: `saveProgress()` → `questProgressRecorder.record()` (quest ID 하드코딩 유지)
- #38: `saveProgress()` + 하드코딩 → `QuestConstants.*` (#37 머지 전 main 기준 브랜치)

**재발 방지 규칙 — 동일 파일 수정 스프린트:**
```
# ❌ 잘못된 방식: 동일 파일 건드리는 스프린트를 origin/main 기준 병렬 브랜치로 생성
git checkout -b refactor/sprint-1 origin/main   # AiCheckService.kt 수정
git checkout -b refactor/sprint-2 origin/main   # 동일 파일 수정 → 충돌 확정

# ✅ 올바른 방식: 먼저 머지 후 다음 브랜치 생성 (직렬)
git checkout -b refactor/sprint-1 origin/main   # 머지 완료 후
git checkout -b refactor/sprint-2 origin/main   # 최신 main 기준으로 새 브랜치
```

**기획 단계 체크리스트:**
- BE 파일 ↔ FE 파일 수정: 병렬 브랜치 OK
- 동일 파일(BE↔BE 또는 FE↔FE) 수정: **직렬 순서 필수** — 앞 PR 머지 후 다음 브랜치

## 다음 작업

- [ ] 다음 기능 기획 (대시보드/통계, UX 개선 등)

## 참조 문서

| 주제 | 문서 |
|------|------|
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
