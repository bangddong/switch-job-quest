# 작업 이력 아카이브

> 상세 검색이 필요할 때만 참조. 현재 상태는 `.claude/CONTEXT.md` 확인.

---

## 인증/정책 분리 시리즈 (2026-04-05)

| Sprint | PR | 내용 |
|--------|-----|------|
| BE Auth | #28 | GitHub OAuth + JWT 인증 (JwtProvider, SecurityConfig) |
| BE 1 | #29 | userId DTO 제거 + @AuthenticationPrincipal + try-catch 정리 |
| BE 2 | #30 | PassCriteriaPolicy + GradePolicy 추출 |
| FE | #31 | GitHub OAuth 로그인 + useAuth + apiClient 리팩토링 |

- `JwtProvider`, `JwtAuthFilter`, `SecurityConfig` — stateless JWT, `/api/v1/auth/github`
- 16개 DTO에서 userId 제거, `@AuthenticationPrincipal`로 SecurityContext 추출
- `PassCriteriaPolicy.evaluate()`, `GradePolicy.from()` — passScore/grade 판단 중앙화

---

## 횡단 관심사 리팩토링 시리즈 (2026-04-04)

| Sprint | PR | 내용 |
|--------|-----|------|
| 1 | #23 | 4-BOSS 합격 기준 이중 판단 제거 |
| 2 | #24 | XP 정책 중앙화 (QuestXpPolicy) |
| 3+4 | #25 | AI 모델 라우팅 + 감사 로그 이벤트화 |

---

## ACT V 5-BOSS 취뽀 달성 화면 (2026-04-03)

- BE: `POST /api/v1/ai-check/journey-report` — 전체 여정 AI 감성 회고 내러티브 생성
- FE: `FinalBossView` — 합격 신고 입력 → 취뽀 타이틀/통계/AI 내러티브/마지막 한 마디

---

## UX 포기 방지 시스템 (2026-04-02)

| Sprint | PR | 내용 |
|--------|-----|------|
| 1 | #18 | E (다음 퀘스트 연결 카드) + F (재도전 코치 + 이전 답변 불러오기) |
| 2 | #19 | B (오늘의 미션 배너) + C (퀘스트 브리핑 화면) |
| 3 | #20 | D (필드별 작성 가이드 `?` 버튼) + G (복귀 배너, BE lastCompletedAt) |
| 4 | #21 | A (온보딩 스토리텔링 5슬라이드 인트로) |

---

## 아키텍처 강화 (2026-04-07)

| PR | 내용 |
|----|------|
| — | AiCallExecutor — devquest.ai.max-retry: 3, null/예외 시 자동 재시도, 3회 실패 시 AiEvaluationException. 13개 Evaluator 전체 적용 |
| — | client-ai/src/main/resources/prompts/*.st — 16개 Spring AI PromptTemplate 파일로 분리 |
| — | InterviewCoachEvaluator 3개, MockInterviewEvaluator 2개로 메서드별 분리 |

### Policy 단위 테스트 보강

| Policy | Before | After |
|--------|--------|-------|
| GradePolicy | 2 | 11 (전 구간 @ParameterizedTest) |
| PassCriteriaPolicy | 6 | 8 (evaluateMax 커스텀 passScore) |
| QuestXpPolicy | 7 | 24 (multiplier 그룹, 고정 XP, 경계값) |

---

## CI/CD 강화 (2026-04-07)

- `copilot-review-evaluator.yml` → `Copilot Review Gate`로 교체 (API 키 불필요)
- Copilot 인라인 코멘트에 답글 없으면 머지 블록
- `Copilot Review Gate / check-copilot-review` → main 브랜치 required check 등록

---

## Feature Dev Team 하네스 구축 (2026-04-06)

- `.claude/agents/` — be-developer, fe-developer, qa-reviewer (Team 모드), be-feature-builder, fe-feature-builder, logic-reviewer, convention-reviewer, test-writer (Sub-agent 모드)
- `.claude/skills/feature-dev/SKILL.md` — TeamCreate 오케스트레이터 스킬
- `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` → `~/.claude/settings.json` env에 설정 완료

---

## 보안 조치 (2026-04-06)

- `application.yml` fallback 시크릿 제거 → `${GITHUB_CLIENT_SECRET}` 형태로 변경
- `application-local.yml` git untrack + 로컬 시크릿 이동
- `.gitignore`에 `**/application-local.yml`, `**/application-secret.yml` 추가
- `fe/src/hooks/useAuth.ts` clientId → `import.meta.env.VITE_GITHUB_CLIENT_ID`

---

## GitHub OAuth 로그인 수정 (2026-04-08)

| PR | 내용 |
|----|------|
| #32 | FE AuthCallback 응답 필드 불일치 수정 (`json.success` → `json.result === 'SUCCESS'`) |
| #33 | Copilot Review Gate 트리거/조건 수정 + Claude 자동 답글 봇 로그인 조건 수정 |

### 인프라 조치 (2026-04-08)
- Cloudflare: `api.quest.dhbang.co.kr` DNS Proxied → DNS only (SSL 핸드셰이크 실패 해결)
- Vercel: `VITE_GITHUB_CLIENT_ID` 환경변수 추가 후 재배포 (`client_id=undefined` 해결)
- GitHub OAuth App: Callback URL `https://quest.dhbang.co.kr/auth/callback` 등록
- 브랜치 보호: Required check 이름 `Copilot Review Gate / check-copilot-review` → `check-copilot-review` 수정

---

## 리팩토링 시리즈 (2026-04-09)

| Sprint | PR | 내용 |
|--------|-----|------|
| Sprint 1 (BE) | #37 | QuestProgressRecorder 추출 (saveProgress/saveHistory 분리) |
| Sprint 2 (BE) | #38 | BaseAiEvaluator 추상 클래스 + QuestConstants 상수화 |
| Sprint 3 (FE) | #34 | gradeUtils.ts + styles.ts 공통 유틸 추출 |
| Sprint 4 (FE) | #39 | ResultHeader + ResultSection 서브컴포넌트 분리 |
| Sprint 5 (FE) | #40 | useAiCheckForm 훅 + FormField 컴포넌트 분리 |

---

## Copilot Gate 구조 수정 (2026-04-10)

| PR | 내용 |
|----|------|
| #41 | assert-not-main.sh — .claude/ 예외 추가 |
| #42 | assert-not-main.sh — jq 없을 때 python3 fallback |
| #43 | Commit Status 동작 검증 (테스트 PR, 머지 안 함) |
| #44 | check run → Commit Status 교체 (gate 구조 수정) |

**근본 원인:** `workflow_dispatch` check run이 PR check_suite에 귀속되지 않아 `mergeStateStatus: BLOCKED` → `--admin` 머지 불가피
**해결:** `createCommitStatus(pending/failure/success)` 사용 — SHA에 직접 기록

---

## Harness 개선 (2026-04-10)

| PR | 내용 |
|----|------|
| #45 | CLAUDE.md — 설계·판단 규칙 추가 (리스크 선제 제시 / 원인 파악 우선) |

---

## 랜딩 페이지 개선 (2026-04-10)

| PR | 내용 |
|----|------|
| #46 | LoginPage — 공감 훅 + ACT I~V 흐름 + CTA + 서브카피 구성으로 확장 |

---

## Observability 구축 (2026-04-12~16)

| PR | 내용 |
|----|------|
| #48 | Sentry Spring Boot Starter 추가 (→ Spring Boot 4.x 미지원으로 PR #52에서 제거) |
| #50 | 구조화 JSON 로그 (logback-spring.xml) + MdcFilter + JwtAuthFilter userId MDC |
| #52 | Sentry 의존성 제거 |

로그 MDC 필드: `requestId` (MdcFilter), `method`/`uri` (MdcFilter), `userId` (JwtAuthFilter)
Logtail (Better Stack): fly.io log drain 연동 완료.

---

## 문서 보강 시리즈 (2026-04-24)

| PR | 내용 |
|----|------|
| #97 | README 아키텍처 보강 — 모듈 의존성, 로그 파이프라인, 배포 구성 추가 |
| #98 | README 인프라 구성도 추가 — Vercel→Fly.io→외부 API 흐름, CI/CD, 외부 의존성 표 |
