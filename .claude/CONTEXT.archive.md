# 작업 이력 아카이브

> 상세 검색이 필요할 때만 참조. 현재 상태는 `.claude/CONTEXT.md` 확인.

---

## 아카이브된 비자명적 결정 (2026-07-07 정리)

### Flyway 마이그레이션 디렉토리 분산 — V8 버전 충돌로 prod 다운 사고 (2026-07-01)
마이그레이션 파일이 `be/core/core-api/.../db/migration/`과 `be/storage/db-core/.../db/migration/`
두 곳에 나뉘어 있고 런타임 클래스패스에서 합쳐짐. PR #231에서 `db-core`만 보고 V8을 새로
만들었는데 `core-api`에 이미 V8·V9가 있어 버전 중복 → Flyway 부팅 예외로 prod BE 완전 다운.
- 수정: `V8__create_tech_question_bank.sql` → `V10__...`로 rename
- 재발 방지(자동화됨): `be-ci.yml` 두 디렉토리 합산 버전 중복 린트 + `systematic-debugging.md` 규칙

### AI 메트릭 소멸 원인 — 반복 OOM 재시작 (2026-07-03, #239)
- Fly 512MB 머신 OOM 킬 → JVM 재시작 → 카운터 리셋 (근본 원인·최종 결론은 CONTEXT.md OOM 섹션)
- #239 대응: `MaxRAMPercentage 75→50`, Metaspace 160m, Xss512k — **재발함** (튜닝으로 불충분)
- `spring.threads.virtual.enabled=true`라서 `server.tomcat.threads.max`는 죽은 설정

## 완료 PR 이력 (2026-05-26 ~ 2026-07-13, 2026-07-15 이동)

| PR/커밋 | 내용 | 날짜 |
|---------|------|------|
| #263 | JVM 메모리 다이어트 — 힙 50%→35%(~179MB), Metaspace 160→128m, `ReservedCodeCacheSize=96m` 신규, `-Xlog:gc` 신규. **⚠️ Metaspace 128m 축소가 prod OOME 다운 유발(붕괴 ~20h, 발견·수정 34h) → #265로 160m 복구.** 힙 35%·CodeCache 96m·`-Xlog:gc`는 유효하여 유지 (힙 실측 42MB로 무죄). 교훈: **실측 없이 상한을 자름** — 당시 Grafana에 작동점 135~137 MiB가 이미 찍혀 있었는데 128m로 잘랐다 (#267에서 원인 확정) | 2026-07-13 |
| #261 | 이력서 PDF 업로드 — pdfjs-dist 브라우저 파싱(dynamic import 지연 로드), 5MB 제한·스캔본 에러·50k자 자르기·덮어쓰기 confirm. **서버 파싱(PDFBox) 구현했다 폐기** — OOM 임계 상태라 서버 부하 0 방향 선택, BE 커밋은 로컬 `backup/be-pdf-parse` 보존. QA 2회, HIGH/MEDIUM 0 | 2026-07-11 |
| #259 | FE tech-debt LOW 3건 — onDelete/onStatusChange 에러 패턴 통일(Promise<void> 전환, swallow 제거), formatSavedAt invalid date 방어, 주석 보완. QA HIGH/MEDIUM 0 | 2026-07-10 |
| #240 | 질문 뱅크 wiki 시드 2026-07 — concept 12건 + V11 질문 21건 (뱅크 총 26건) | 2026-07-05 |
| #239 | OOM 대응 JVM 메모리 예산 튜닝 (힙 50%, Metaspace 160m, Xss512k) — 이후 재발, #245로 이어짐 | 2026-07-04 |
| #237 | 질문 뱅크 wiki 시드 반자동화 — /question-bank-seed 스킬 + SessionStart 훅 | 2026-07-03 |
| #236 | Grafana AI Metrics 토큰 패널 table 전환 + 대시보드 v2 스키마 IaC 동기화 | 2026-07-02 |
| #231 | 기술면접 질문 뱅크 DB — TechQuestionBank + DailyMailScheduler 뱅크 우선 조회→AI 폴백 | 2026-07-01 |
| #230 | repo 정리 — daily 로그 24건 커밋, qa-cache gitignore 보강 | 2026-07-01 |
| #229 | 지원 파이프라인 Phase 2 — JD 분석 코칭 연동 | 2026-06-30 |
| #228 | K8s Stage 3 학습 인덱스 — PostgreSQL StatefulSet + PV/PVC 예습 | 2026-06-29 |
| #227 | 회사별 지원 파이프라인 Phase 1 — AppliedCompany CRUD + 지원 현황 UI | 2026-06-28 |
| #226 | 데일리 기술면접 참고자료 — 국내 컨퍼런스 발표 카테고리 주입 | 2026-06-27 |
| #224 | K8s Stage 2 학습 인덱스 — ConfigMap/Secret 패턴 예습 노트 | 2026-06-24 |
| #223 | K8s Stage 1 학습 기록 — 단계별 명령어 + 용어 심화 설명 | 2026-06-24 |
| #222 | 데일리 질문 꼬리질문 제거 → 모범답안 실무 포인트 섹션 추가 | 2026-06-24 |
| #221 | K8s 초기 매니페스트 — BE Deployment, Service, env-requirements.md | 2026-06-24 |
| #220 | modelAnswer 길이 축소 — output 토큰 ~6000 → ~2000 목표 | 2026-06-22 |
| #217 | OtlpMeterRegistry 중복 start() 제거 + push 성공 로그 추가 | 2026-06-20 |
| #216 | OTLP auto-config 명시 비활성화 | 2026-06-20 |
| #215 | prompt injection 방어 — BaseAiEvaluator callAi/wrapUserContent, 17개 Evaluator | 2026-06-18 |
| #214 | TechInterview max-tokens 4000→8000 + 대시보드 카운터 round() | 2026-06-17 |
| #213 | OTLP push keep-alive stale connection 수정 | 2026-06-17 |
| #212 | OTLP auto-config @SpringBootApplication excludeName 제외 | 2026-06-17 |
| #208 | 기술면접 평가 면접관 페르소나 수정 — 5년차 기준 | 2026-06-16 |
| #206 | 평가 결과 마크다운 렌더링 + 모바일 가시성 개선 | 2026-06-15 |
| #203 | qa-reviewer deprecated API 전수 확인 체크리스트 추가 | 2026-06-13 |
| #202 | 전체 Evaluator .entity() → parseContent() — Spring AI RC2 500 수정 | 2026-06-13 |
| #191 | qa-reviewer + orchestrator severity 기준 통일 | 2026-06-10 |
| #190 | 기술면접 비로그인 체험 + IP rate limiting (Bucket4j) | 2026-06-10 |
| #189 | PR 리뷰 훅 HIGH/MEDIUM/LOW 3단계 | 2026-06-10 |
| #187 | AI 캐시 토큰 메트릭 + Grafana 대시보드 | 2026-06-09 |
| #186 | Better Stack 제거 — Grafana Cloud Loki 전환 | 2026-06-09 |
| #185 | QA 강제화 훅 — gh pr create 전 차단 | 2026-06-09 |
| #184 | 모의면접 Java/인프라 카테고리 추가 + 모범 답안 상세화 | 2026-06-09 |
| #183 | AI 호출 메트릭 수집 — AiMetricsRecorder | 2026-06-08 |
| #180 | 데일리 메일 deepLink URL 오타 수정 | 2026-06-08 |
| #178 | fe-feature-builder·design-reviewer ultrathink 제거 | 2026-06-08 |
| #176 | Grafana instance ID 수정 | 2026-06-08 |
| #177 | CI claude-review 폐기 → PreToolUse 훅 사전 리뷰 | 2026-06-08 |
| #174 | Copilot 리뷰 → Claude 리뷰 전환 | 2026-06-07 |
| #173 | 데일리 질문 중복 발송 방지 — 최근 30일 제외 | 2026-06-07 |
| #172 | Grafana Alloy 제거 + Micrometer OTLP push 전환 | 2026-06-07 |
| #165 | 모바일 코딩 에디터 CodeMirror 6 교체 | 2026-06-04 |
| #163 | 모바일 코딩 에디터 스크롤·잘림·키보드 수정 | 2026-06-02 |
| #162 | 메일 HTML 템플릿 개선 + dhbang.co.kr 도메인 인증 | 2026-06-01 |
| #161 | 데일리 메일 AI 질문 생성 + 발송 이력 중복 방지 | 2026-05-31 |
| #160 | 카테고리별 코딩 풀이 레이더 차트 (SVG, 9축) | 2026-05-31 |
| #158 | 코딩 로드맵 해금 기준 DISTINCT 수정 | 2026-05-30 |
| #157 | 코딩 문제 로드맵 — 9개 카테고리 잠금/해금 | 2026-05-29 |
| #156 | 모바일 UX 개선 — AI 폼 다단계화 등 | 2026-05-28 |
| #155 | PR 절차 문서화 — git-strategy.md | 2026-05-27 |
| #154 | orchestrator tools 와일드카드 변경 | 2026-05-27 |
| #153 | .claude 구조 개편 — skills 분리, pair-programmer 추가 | 2026-05-26 |

## 완료된 다음 작업 항목 (2026-07-07 이동)

- [x] Grafana AI Metrics 토큰 패널 table 전환 (#236) — instant 쿼리 + merge/organize transform.
      Overview stat 패널 `$__range` 연동은 보류 (필요 시 별도 작업)
- [x] client-ai Jackson 2/3 혼재 정리 (#243) — root 전역 Jackson 2 kotlin 모듈은 유지
      (victools transitive 필요, SB4 병행 패턴) — 전체 교체는 별도 범위
- [x] 기술면접 질문 뱅크 1차 확충 (#240) — 이후 SessionStart 훅 알림 따라 `/question-bank-seed` 반복
- [x] RESEND_API_KEY / JUDGE0_API_KEY 발급 → local yml + Fly secret 세팅 완료
- [x] devquest-log-shipper 제거 — 커스텀 Logback HTTP 어펜더로 대체
- [x] Disambiguation Gate + Closing Summary 에이전트 패턴 도입 (#127)

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
