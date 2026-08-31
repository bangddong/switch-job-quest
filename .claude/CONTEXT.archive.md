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
| #266 | AWS EKS 학습 놀이터 계획 문서화 (`infra/aws-eks/README.md`) | 2026-07-15 |
| #265 | **Metaspace 160m 복구 — 프로덕션 다운 핫픽스.** #263의 128m 축소가 `OutOfMemoryError: Metaspace` 유발. 힙은 무죄(42M/179M) — GC 트리거가 전부 `Metadata GC *` 계열이었음. 단일 값만 복구, 힙 35%·CodeCache·`-Xlog:gc` 유지. QA HIGH 0. 머지·BE CD 배포 성공(07-14). 누수 검증 완료(07-15) — 누수 없음, 160m 적정 (CONTEXT.md "비자명적 결정" 참조) | 2026-07-14 |
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

## 2026-07-28 아카이브 (CONTEXT 최근완료 3건 유지 규칙에 따라 이관)

| PR/커밋 | 내용 | 날짜 |
|---------|------|------|
| #334 | **FE 테스트 문서 동기화 (chore, 코드 변경 0).** #331이 남긴 문서 부채 정리. `tdd.md`가 여전히 *"FE 테스트 러너 미도입"*이라 단언 중이었는데, 이 스킬은 orchestrator가 FE 작업 시 **전문을 주입**하는 파일이라 방치하면 에이전트가 러너를 안 쓴다(만들어놓고 사장). ①`tdd.md` 문구 교정 + FE 예시를 **globals 미설정 기준 명시 import**로 교체(기존 예시는 그대로 쓰면 `tsc` 깨짐) ②`verification-before-completion.md`에 `npm test` 추가(CI 게이트 명시) ③**`fe/CLAUDE.md`에 테스트 섹션 신설**(기존엔 테스트 언급 전무) — 러너·실행법·CI 게이트·명시 import 필수·파일 위치 + **한계**(`environment:'node'`라 컴포넌트 테스트 불가, jsdom 미도입 → `tsc`+`build`로 대체). 문서 주장을 실행으로 검증(예시 단언을 임시 테스트로 실제 통과 확인 후 삭제). | 2026-07-27 |
| #333 | **데일리 질문 중복방지 윈도우 버그 수정 (fix).** `findRecentQuestions(type, 30)`의 30이 일수가 아니라 **행 수**였고, 로그는 `forEach { save(userId, ...) }`로 **사용자당 1행/일** 쌓이는데 쿼리에 `DISTINCT`가 없었다 → **커버 기간이 30/N일로 축소**(1명 30일 / 10명 3일 / 30명 1일=사실상 무력화). 포트를 `findQuestionsSince(type, since: LocalDateTime)`로 개명하고 JPQL을 `sentAt >= :since`로, 중복 제거는 **어댑터 Kotlin `.distinct()`**(Postgres는 `SELECT DISTINCT`+`ORDER BY 비선택컬럼`이 에러라 SQL DISTINCT 회피). **윈도우 20일 = 뱅크 26개보다 작아야 AI 폴백이 안 돈다**(≥26이면 주기적 소진 → AI 비용 신규 발생, 상수 주석에 근거 명시). TDD: 프로덕션 코드만 stash해 RED 확인 후 GREEN. 신규 `DailyMailLogAdapterTest`. QA가 **JPQL 문자열을 직접 읽어** 대체 검증(Mockito verify+argumentCaptor)이 거짓 안심이 아님을 판정. HIGH0·MED0·LOW2(F-1 wontfix=순서는 의미 갖는 소비처 없음 / **F-2 deferred→원장 L-9 = zone 불일치**). | 2026-07-27 |
| #331 | **FE 테스트 러너(vitest) 도입 + CI 테스트 게이트 (chore).** #329에서 순수함수 단위테스트를 못 붙인 갭 해소. Vite 6 스택 재사용 → vitest ^3.2.7, 대상이 전부 순수함수라 `environment: 'node'`(jsdom·@testing-library 미도입, 컴포넌트 테스트는 범위 밖). `vite.config.ts`에 `test` 필드 병합(별도 config 안 만듦), 글로벌 대신 명시 import. `extractPdfText.ts` 순수함수 4개 **단위테스트 18개**: normalizeExtractedText(CRLF/lone CR·공백/빈줄 병합·trim·idempotent) 7 + truncateExtractedText(50000 경계) 3 + validatePdfFile 5 + PdfExtractError(cause 보존) 3. `test:"vitest run"`. **QA F-1(MED)=fixed**: fe-ci.yml에 `npm test` 게이트 없어 로컬만 초록불이던 것 → Lint·Build 사이 Test 스텝 추가(의도적 실패로 non-zero exit 게이트 동작 검증). QA 실측: 테스트가 실제 소스 import(tautology 아님)·전 케이스 정규식 순서와 일치. tsc0·build·lint(무관 warn 1)·18 passed. | 2026-07-27 |
| #329 | **FE tech-debt 정리 (fix).** ①CompanyCard 연타 중복요청 가드(#259): `deleting`·`changingStatus` state + `busy` 확장으로 삭제·상태변경 중복 발사 방지(finally로 항상 복구). ②extractPdfText(#261): `doc.destroy()`가 원 예외 덮던 것→자체 try/catch+`console.warn`, `normalizeExtractedText`에 `\r\n?`→`\n` 선정규화(CRLF/lone CR), 실패 단계 3분(모듈로딩/파싱/페이지추출)을 각각 `PdfExtractError` 사용자 메시지로 구분. QA HIGH0·MED0·LOW4 전부 fixed(2건 초기+2건 재검토 발견). tsc0·build·eslint 통과. ⚠️ **FE 테스트 러너 부재로 단위 커버 없음**(알려진 갭). | 2026-07-27 |
| #328 | **db-core 마지막 Jackson 2 잔재 제거 (refactor, 동작 무변경).** `CodingProblemAdapter`를 J3로: `build.gradle.kts` J2 kotlin 모듈→`tools.jackson.module`, 코드는 `jacksonObjectMapper()`+reified `readValue<List<TestCase>>()`. **QA 실측: J2 저장 JSON→J3 역직렬화→재직렬화 bit-identical 증명.** 회귀 가드 신설 `CodingProblemAdapterTest` 3케이스(레거시 J2 JSON 고정 문자열 파싱 포함, QA F-1 fixed). db-core 24 tests. **정정: client-ai evaluator들은 원래 J3였음**(#327 노트 오류) — be/ 소스 J2 잔재 이제 0건. QA F-2(전역 J2 kotlin 모듈 dead weight)=deferred→원장 L-8. | 2026-07-27 |
| #327 | **core-api 잔여 Jackson 2 제거 (refactor, 동작 무변경).** `CodingQuestService`·`TechInterviewRateLimitInterceptor`·`DailyExplainRateLimitInterceptor` 3곳의 인라인 `com.fasterxml.jackson...ObjectMapper()` → Boot4 자동구성 J3(`tools.jackson.databind.ObjectMapper`) **생성자 주입**으로 통일(다른 서비스/어댑터와 동일 방식). 세 곳 다 `writeValueAsString(map)` 단순 직렬화뿐이라 wire JSON 불변(원시값 Map). `grep com.fasterxml.jackson core-api/src/main`=0건. **백로그 "요청마다 ObjectMapper 생성"은 부정확**이었음(전부 lazy/필드=인스턴스당 1회) → 실제 값은 잔재 제거. QA HIGH0·MED0·LOW1(trailing comma, `7d20d0e` fixed). 239 tests 0 failures. **범위 밖(후속): client-ai Evaluator·db-core CodingProblemAdapter는 J2 잔존**(AiHttpClientConfig 주석대로 core-api HTTP 계층만 J3 스코프) | 2026-07-27 |
| #326 | **죽은 설정 키 제거 (chore, 동작 무변경).** `be/` 소비처 0인 설정 3곳 삭제: `devquest.ai.pass-score`·`interview-questions`(@Value grep 0건) + prod `server.error` 블록(Boot 4는 `spring.web.error`로 이관, `server.error.*`는 무시되고 기본값 `never`로 동작 중이라 현재 값과 동일 → 삭제해도 불변). **`max-retry`는 유지**(AiCallExecutor 소비, Phase 3 전 제거 금지=inprocess 롤백 불변식). `./gradlew build` 239 tests 0 failures. 출처=백로그 #306·#308 QA LOW | 2026-07-27 |
| #324 | **EKS Stage 1 — 첫 앱 배포 실증 (2026-07-27, ★과금 ~$0.05).** core-api를 EKS에 배포하는 매니페스트(`k8s/base/core-api.yaml`, Deployment+Service ClusterIP) + apply→배포→teardown 왕복. **핵심: ECR→노드 이미지 pull이 imagePullSecret 없이 노드 IAM 역할(`AmazonEC2ContainerRegistryReadOnly`)로 성공**(3.2s, arm64↔Graviton 실측 일치). DB 없이(B안) 진행 → **CrashLoop 3단계 진단**: 부팅 순서상 ①Loki 로깅(`grafanaLokiUrl` 미설정→`URI undefined scheme`) ②JWT_SECRET ③DB(HikariPool) — "Fly secrets가 가려주던 숨은 환경 의존이 플랫폼 이전 시 드러남". 클린 teardown(고아 0·ECR 이미지 생존=#322 결정 작동). **QA가 거짓 자신감 포착**: grep 팁이 실제 인시던트 원인이던 Loki env var를 놓친 것(F-4)을 재실행으로 발견→수정. 퀴즈 1.5/5(재검토 4). ClusterIP 선택=LoadBalancer의 NLB 고아 회피. 선행 #322(ECR 0-bootstrap 편입)·#323(arm64 CI 빌드 워크플로, OIDC 신뢰정책상 PR 컨텍스트 빌드) | 2026-07-27 |
| #320 | **EKS 과금 안전장치 — dead man's switch (2026-07-25).** "apply하고 destroy 잊고 세션 종료"를 기계로 차단(사용자가 끝 신호 못 줘도 동작). 3조각: `eks-session-marker.sh`(PreToolUse, `tofu apply` 감지→마커) + `eks-heartbeat-reminder.sh`(Stop, 매 턴 하트비트 갱신+경고+자가청소) + `eks-reaper.sh`(launchd 30분, 하트비트 **2h stale**이면 실제 클러스터 확인 후 `tofu destroy`). Claude로 작업 중이면 하트비트 갱신돼 안 죽임, 사라지면 2h 뒤 자동 destroy. **tofu state 존중**(로컬 실행). 한계: macOS 잠들면 미실행(최악 주말 ~$6, $35 예산알람 backstop). 경계 12경우 전수 테스트. **이 맥에 launchd 설치·로드 완료**(새 머신=`install-reaper.sh`, TASKS.md TASK-7). SOP=`docs/eks-session-sop.md`. | 2026-07-25 |
| #318 | **EKS 학습 퀴즈 게이트 — 이해 검증 기계 강제 (2026-07-24).** Task 8에서 CLAUDE.md "학습 설명 의무"(prose)가 momentum에 밀려 실제 누락된 것을 교정. QA 루프(`assert-qa-run.sh`)와 동일 철학으로 **구축 후 이해도 퀴즈를 훅으로 강제**. `assert-eks-quiz.sh`: `stage/eks-*` 브랜치 `gh pr create` 시 `docs/eks-quizzes/<br>.md` + `<!-- QUIZ-PASSED -->` 마커 없으면 차단(5경우 전수 테스트). `quiz.md`에 "EKS 학습 마일스톤 모드"(실측 로그 기반 문제·기록·마커), orchestrator 9.5단계 필수화, CLAUDE.md 브랜치 규칙에 `stage/eks-*` 추가. **첫 적용=Stage 1부터.** 리허설(Task 8 내용 5문제) 2/5 — 게이트 효용 실증(구축됐지만 이해 구멍 드러남). | 2026-07-24 |
| Task 8 (문서 PR) | **★ EKS 첫 과금 왕복 실증 (2026-07-24).** `tofu apply`(14 added, ~10분) → `kubectl get nodes` **노드 Ready**(v1.36.2·arm64 Graviton·공인IP·NAT 회피 확인) → `tofu destroy`(14 destroyed) → **teardown 전수검증 고아 0**(state·EBS·SG·LB·NAT 전부 없음). **2-cluster IaC가 실제로 뜬다-부순다 왕복 동작함을 실증.** 비용 **~$0.1 이하**(벽시계 ~50분, 컨트롤플레인 40분×$0.10). 사전점검서 kubectl 미설치 발견→`brew install`(v1.36.3). K8s 1.36 표준지원 재확인. #314의 ON_DEMAND 노드 정상 프로비저닝. 문서: `eks-migration-log.md` 실측 + `eks-tutorial-steps.md` Step 8 정답경로. **다음=Stage 1(ECR 편입 후 앱 배포).** | 2026-07-24 |
| #314 | **EKS node `capacity_type` 변수화 + Free Plan 실측 반영 (Task 8 선행).** `nodes.tf`의 `capacity_type="SPOT"` 하드코딩 → `var.node_capacity_type`(기본 **ON_DEMAND**) — 계정 API 실측 **Spot vCPU 쿼터=0**이라 SPOT이면 apply 필패. 스팟 학습 시 쿼터 증액 후 tfvars 주입(온디맨드↔스팟 650h 차 $13). **Free Plan 3자 대조 확정**(API·공식문서·한국어랜딩): 크레딧 **$199.81**/만료 **2027-01-15**/EKS 제한 대상 아님(쿼터 100). 🔴 **크레딧 소진도 계정 폐쇄 트리거**("depleted OR duration ends") → 안전예비 $30 규칙. 🟡 Organizations 등 자동 Paid 전환 주의·🟢 잔여크레딧 이월. tfstate 없음(apply 전)이라 replace 무. **prod(Fly+Neon) 무영향.** 만료일 캘린더 등록 = TASKS.md TASK-6 | 2026-07-23 |
| #308 | **Phase 1 Task 1.4b·1.5 — 트랜잭션 경계 재배치 + parity 검증 (Phase 1 완료).** Task 0.1~1.4a를 통틀어 **처음으로 프로덕션 동작을 바꾼 PR.** ①Jackson 2→3 교체(ai-api와 동일 라이브러리로 통일, 1.4a QA 지적) + read-timeout 90s→150s(기존값이 "30초×재시도3회"와 마진 0) ②**parity 라운드트립 12 tests** — core-api에 전용 소스셋 `parityTest` 신설(일반 test에 넣으니 `scanBasePackages="com.devquest"`로 ai-api가 딸려와 233개 중 43개 회귀 → 클래스패스 격리), 실제 ai-api 기동 + AI 포트만 목 → in-process와 HTTP 결과 **정확 일치** 비교. Map·List<중첩>·nullable·default 생략·text/plain·400/500 전부 커버 ③**트랜잭션 재배치 14개 메서드**(AiCheckService 11 + TechInterviewService.evaluate + CompanyService 2) — 전부 "AI 호출→단일 쓰기" 패턴이라 바깥 `@Transactional`이 애초에 추가 원자성을 안 줬음(QA가 호출 그래프로 독립 검증). `CodingQuestService` 2건은 재시도 루프에 뒤섞여 **의도적 보류**. HikariCP pool=10 고갈 위험 제거. 회귀 가드 6건("AI 실패→쓰기 `never()`"). **🐛 parity가 진짜 버그 검출**: `server.error.include-message`가 Boot 3 키 → Boot 4는 `spring.web.error.*` = AI 실패 원인이 core로 **한 번도 전달된 적 없었음**. core-api 239 tests + parity 12 + ai-api 41 전부 0 failures. Fly 무영향(bootJar task graph에 ai-api 0개) | 2026-07-22 |
| #307 | **Phase 1 Task 1.4a — core HTTP 어댑터 배선 (무행동).** Task 1.4를 기계적 배선(1.4a)과 동작 변경(1.4b)으로 **분리**. 어댑터 18개/엔드포인트 24개, `BaseAiHttpAdapter`가 직렬화·에러 매핑 흡수. `AiTransportConfig`를 18개 포트로 확장(어댑터에 `@Component` 안 달아 inprocess에선 빈 생성조차 안 됨 — `getBeanNamesForType(RestClient)` 비어있음으로 증명). **계획이 예고한 함정 4종 처리**: ①타임아웃 명시(무한 대기 방지) ②**재시도 미도입**(ai-api 안 `AiCallExecutor`가 이미 3회 → 또 하면 최대 9회 실제 LLM 호출·비용 폭증) ③**Accept 406 회피**(`text/plain` 2개 + JSON 22개를 `.accept()` 없이 String 수신, JDK HttpServer로 진짜 협상 재현 실측) ④에러 전파. core-api 229 tests(기존 161 보존 + 신규 68) | 2026-07-22 |
| #305 | **Phase 1 Task 1.1 — ai-api REST 컨트롤러 (Phase 1 본체).** client-ai 부착(`scanBasePackages="com.devquest"` 확대, db-core 미의존 유지) + AI 포트 17개 전 메서드 23개 + Judge0 = **엔드포인트 24개** 노출. 응답은 core-domain data class 그대로(계약 단일 출처). Kotlin default 파라미터 소실 3건 서버측 복원. **🔴 QA HIGH 1건 — 실측으로 확정**: `produces=APPLICATION_JSON_VALUE`를 붙여도 `StringHttpMessageConverter`가 `*/*` 지원으로 Jackson보다 먼저 선택 → **헤더는 JSON, 바디는 따옴표 없는 raw text**. `@SpringBootTest(RANDOM_PORT)`+JDK HttpClient로 바이트 실측 후 `text/plain;charset=UTF-8`로 정정. **테스트가 거짓 안심을 준 구조도 제거**(`jsonPath("$")`는 json-smart permissive라 bare word 통과) | 2026-07-22 |
| #304 · #306 | **Phase 1 Task 1.3(#304) — ai-api `AiCallLogPort` 관측 어댑터.** Phase 1 나머지의 선행 조건(client-ai를 db-core 없이 붙이기 위함). 구조화 로그 + Micrometer만, DB 접근 0. 토큰 4종 전부 로그 보존(비용 추적 공백 없음). / **Task 1.2(#306) — 설정 이관.** client-ai `@Value` 5건 전수 조사 → `max-retry`·`judge0.*`를 ai-api에 **동일 값으로** 명시(기본값에 우연히 의존하던 상태 해소). **core-api에선 키를 지우지 않음** — inprocess가 여전히 client-ai 빈을 호스팅하므로 롤백 보존, Phase 3 정리. 부수 발견: `devquest.ai.pass-score`·`interview-questions`는 소비처 0건(죽은 설정) | 2026-07-22 |
| #295 | **서비스 분해 Phase 0 Task 0.1 — AI 포트 마커 (무행동 변경).** 향후 ai-service 추출 대상을 타입으로 식별 가능하게: `port/ai/AiEvaluatorPort.kt` 빈 마커(순수 Kotlin) 생성 + AI(LLM) 포트 **정확히 17개**에 상속 표식만 추가(시그니처·반환 무변경). `*EvaluatorPort` 10 + `*Port` 7(TechInterview·InterviewCoach·CodingHint·SkillAssessment·JourneyReport·ActClearReport·CodingProblemGenerator). **Judge0Port 제외**(비-LLM 코드채점). 규약 테스트 `ArchAiPortConventionTest`로 "정확히 17개 + Judge0·DB 포트 14개 제외" 고정. QA 실측: 전체 모듈 재컴파일 회귀 0·core-api bootJar 정상(Fly 무영향). HIGH 0·LOW 2(규약 테스트 jar스킴/dedupe 견고성, 무해). 커밋 316509b | 2026-07-21 |
| #292 | **서비스 분해 Phase 0~1 구현계획 확정 (ai-service 추출).** 설계(#289)를 태스크로 분해하기 전 **Blindspot Pass 진단**으로 "어댑터만 스왑" 가정을 실제 코드와 대조 → **부분 일치**, 4개 불일치를 Phase 0 선행 태스크로 반영: ① AI 포트 **17개** 중 7개가 `*EvaluatorPort` 미준수라 DB 포트와 혼재 → 마커 인터페이스(Task 0.1) ② `CacheMetricsAdvisor`→`AiCallLogPort`→db-core로 **매 호출 core DB write**(숨은 역결합) → **관측 재배치 A안**(Task 0.2/1.3) ③ `max-retry`·Judge0 설정이 core-api에 분산(Task 1.2) ④ `AiCheckService` `@Transactional` 안 AI+DB 혼용→HTTP 지연 유입(Task 1.4). 안전장치: 피처플래그(`transport=inprocess↔http`) strangler 롤백, 계약=`core-domain` data class 단일출처. Phase 0=무행동 변경, Phase 1 완료판정=parity+즉시롤백. 계획 `docs/superpowers/plans/2026-07-21-service-decomposition-phase01.md`. 코드 0줄. 머지 완료 | 2026-07-21 |
| #289 | **서비스 분해 설계 확정 (제품 방향 전환).** "만든 사람조차 안 쓴다" → 무거운 12기능 앱을 **가볍게 매일 쓰는 데일리 도구**로 재정렬 + EKS 다중서비스 학습. 모듈러 모놀리스를 **daily + ai-service + core 3서비스**로 분해. **핵심 발견**: AI 경계가 이미 `core-domain` 포트(`*EvaluatorPort` 18종)로 존재 → 어댑터 in-process→HTTP 스왑으로 추출. strangler 순서(ai→daily→core 유지), 모노레포 멀티모듈, ai=NetworkPolicy만·공유DB 스키마분리·daily FE는 Phase2 동반. 스케일링(이메일 Resend 무료~100/일 천장→SES·토큰·캐싱)·EKS 인프라 영향(2-cluster addon NetworkPolicy·노드용량)·배포전략(EKS=실습/Fly=fallback) 포함. 설계 문서 `docs/superpowers/specs/2026-07-20-service-decomposition-design.md` + 브리핑 아티팩트. 코드 0줄. 머지 완료 | 2026-07-20 |

<!-- 07-29 클린 클로즈 시 이관 -->
| **#339** | **★ EKS Stage 2 실증 완료 — RDS + IRSA + External Secrets Operator (2026-07-28, 과금 26분 35초 ≈ $0.06).** apply→검증→teardown 왕복. **Stage 1에서 CrashLoop로 끝났던 core-api가 코드 변경 0으로 `/health` 200** (RDS `sslmode=require` 접속, Flyway 12개 적용, 26.3초 기동) — 바뀐 건 환경변수 주입 경로뿐. 시크릿은 **손으로 안 만들었다**: ESO(v2.8.0)가 Secrets Manager에서 K8s Secret 2개(10키) 자동 생성. **🔴 가장 값진 실패 = 한글 `description`으로 apply가 리소스 2개에서 깨짐 — `validate`·`plan`·`tfsec`이 셋 다 통과시킨다**(AWS API 미호출). 게다가 OpenTofu는 에러를 **종료 시점에 몰아 출력**해 "Creating..."에 멈춘 것처럼 보였고 `tofu state list`도 apply 중엔 무용 → **CloudTrail 원문**으로 확정(요약 `ErrorCode`는 `None`이라 오독 유발). 제약은 **서비스마다 다름**: EC2 SG·IAM ❌ / Secrets Manager·ECR ✅. **미검증 5건 전부 해소**(kubectl 스키마·ESO CRD는 `v1`만 served·IRSA `sub` 일치·**RDS 관리형 시크릿 자동 정리**·RDS 생성 4m50s/삭제 3m53s). **🔑 IRSA 두 실패 모드 실측 구분**: `sts:AssumeRoleWithWebIdentity` 거부=**인증**(sub/aud) vs `no identity-based policy`=**인가** — 후자는 주체가 `assumed-role/...`로 찍히는 것 자체가 assume 성공 증거. **teardown 고아 0건.** Blindspot Pass가 잡았던 과금 구멍 3개 전부 실전 유효. QA HIGH0·MED1·LOW2 전부 fixed. 퀴즈 3/4+재검토 통과. ⚠️ `be/` 변경(logback 조건부화) 포함 → **prod에도 배포됨**(prod는 LOKI URL이 설정돼 동작 무변경) | 2026-07-28 |
| #336 · #337 | **timezone — 오진과 정정 (교훈이 본체).** #336에서 "저장은 ambient zone·조회는 KST라 어긋난다"며 데일리 메일 마진0·스트릭 과소계산을 주장하고 `-Duser.timezone=Asia/Seoul`을 넣었으나, **#337에서 로컬 docker(colima) 설치 후 실측하니 오진**: `eclipse-temurin:*-alpine`엔 tzdata가 **있고** `TZ=Asia/Seoul`만으로 `systemDefault()=Asia/Seoul`. prod는 **#210부터 쭉 KST**였고 불일치는 존재한 적 없음(실제 앱 이미지 빌드·부팅 → GC 로그 `+0900` 확인). **오진 경로 2단계**: ①없는 경로(루트 `fly.toml`) grep → `\|\| echo "없음"` 폴백을 사실로 보고 ②QA가 TZ 존재를 찾은 뒤에도 **이미지 실측 대신 웹 검색 일반론 채택**(기존 결론을 살려주는 방향이라 더 위험). → 두 패턴을 `systematic-debugging` 스킬에 등재. **잔존 가치(L-9-c)**: `build.gradle.kts` 테스트 zone 인자는 **실재하던 CI≢prod 갭**(CI=UTC, prod=KST)을 닫았고 `TimezoneConsistencyTest`가 가드. 원장 L-9/L-9-b는 obsolete 재분류. **🐳 colima+docker CLI 로컬 설치됨.** | 2026-07-28 |
| #335 | **CONTEXT 클린 클로즈 (07-27 세션 종료).** | 2026-07-27 |

<!-- 07-29 2차 클린 클로즈 시 이관 -->
| #340 | **CONTEXT 클린 클로즈 (Stage 2 종료).** 최근완료 19건 아카이브 이관, "Stage 2가 다음" 잔존 참조 3건 정정. | 2026-07-28 |

## 완료 PR 이력 (2026-08-03 이동)

| PR/커밋 | 내용 | 날짜 |
|---------|------|------|
| **#342 · #343** | **★ IaC-first 정합화 — 그리고 가드레일이 사실은 꺼져 있었다는 발견 (07-29, 비용 $0).** 사용자 지적("IaC 기반인데 캡처가 필요하냐")에서 출발했으나 밑에 더 큰 부채가 있었다: 튜토리얼 G-1이 **콘솔 클릭으로 예산 만드는 법**을 가르치는데 `budget.tf`가 **이미 그 절차를 대체**한 상태 → 캡처는 죽은 절차의 부속품. 캡처 체계 전면 폐기 + 콘솔 절차를 IaC로 교체 + **미작성이던 레이어 스캐폴딩(재현 검증 첫 구멍) 작성**. 이상탐지를 `cost-anomaly.tf`로 코드화(TASKS는 "코드로 처리"라 적었지만 **실제 .tf가 없었다**). **🔴 머지 후 CI apply 실패 — `Limit exceeded on dimensional spend monitor creation`**: AWS가 신규 계정에 `Default-Services-Monitor`를 미리 만들어 두고 DIMENSIONAL은 **계정당 1개** → 생성이 아니라 **인수(import)**가 정답. plan은 내 코드만 보므로 못 잡는다(**plan이 못 잡는 실패 3번째** — ①한글 description ②EMAIL+IMMEDIATE ③계정당 한도). **진짜 수확**: 인수해 보니 AWS 기본 임계값이 **$100 AND 40%** — 크레딧 $200 계정에서 **절반 날아간 뒤에야 울림**. 콘솔엔 "켜짐"으로 보여 **꺼진 것보다 나빴다**. $5로 하향, 실측 확인 완료. 부수 발견: **크레딧 만료일이 문서마다 달랐고**(01-15 vs 07-15) 사용자 확인으로 **2027-01-15 확정** — 07-16의 "가입 +1년" **추론이 콘솔 값을 덮어쓴 오독**이었다. **크레딧 수명이 1년이 아니라 6개월.** | 2026-07-29 |
| **#349** | **★ EKS Stage 3a (머지 완료) — RDS를 in-cluster Postgres로 전환 (07-30, 과금 27분 $0.06, 고아 0).** PVC Bound 11초 · EBS 실물(10GiB gp3 2a 암호화) · Flyway 12개 · `/health` 200 · **파드 삭제 후 데이터 유지 확인**(UID 변경, 볼륨·26행 동일) · PVC 삭제 후 EBS 7초 회수 · `destroy 30` 전수 0. **발견 ①** `GRAFANA_API_KEY` 소비처가 **둘**이었다 — logback(`5cf76da`)은 고쳤지만 `OtlpMetricsConfig`가 남았고, 그 가드는 `application-prod.yml`의 하드코딩 `enabled: true`만 본다(키 유무 무관). CONTEXT에 적힌 *"이게 통과하면 검증 끝"* 이라는 전제가 불완전했다 → BE 후속 등재. **발견 ②** **관리형이 공짜로 주던 것에 TLS가 있었다** — `sslmode=require`만 jdbc-url에 상수라 RDS에선 안 드러났다(`The server does not support SSL`). tofu `tls_self_signed_cert`→SM→ESO로 해결(Stage 2 파이프 재사용, 새 개념 0). **⭐ 예방 1건**: apply 전 `describe-addon-configuration`으로 `replicaCount` 기본값 2를 발견해 1로 — 안 했으면 12>11로 막혔다. **"plan이 못 잡는 실패"를 사후 아닌 사전에 막은 첫 사례**(앞의 3건은 전부 깨지고 나서 알았다). **설계 절차 첫 적용**: 착수 전 D-001 영향 범위 조회에서 **CONTEXT 내부의 정면 충돌**(영속 레이어는 동적 PVC 배제 / Stage 3 서술은 동적 전제)을 발견 → `D-004` 신설, 3a(동적)/3b(static)로 분할. ⚠️ 파드 11/11 여유 0 → `rollout restart` 실패(`Too many pods`), `scale 0→1`로 우회. QA: HIGH 0, F-1 fixed, F-2·F-3 → 원장 L-9·L-10. | 2026-07-30 |
| **#387** | **★ Phase 2 Stage A — daily-question 읽기 경로 자립 (08-18, 비용 $0).** `GET /api/v1/daily-question` 이 **매일 00:00~09:00 KST(하루 9시간)** 404였다 — `ensureTodayQuestion()`의 유일한 main 호출자가 09:00 cron이었기 때문. 에러 문구가 *"오전 9시 이후 다시 확인해주세요"* 라 **의도된 동작으로 문서화돼 있었다**. → 읽기 시 **뱅크 전용** lazy 생성(AI 미호출), 소진 시 404 유지. 🔴 **착수 시 내가 추천한 안(AI 포함 lazy)은 이미 기각된 G-2(b)였고 Blindspot Pass가 잡았다** — 같은 파일 10줄 아래 `기각한 선택지 (재론 방지)`를 안 읽었다. 기각 사유를 실측했더니 **예상보다 나빴다**: prod는 `transport: inprocess`라 `read-timeout-ms`가 적용 안 돼 **AI 호출에 타임아웃이 없고**, GET은 `permitAll`+레이트리밋 없음, UNIQUE는 `save()`만 dedup이라 **저장은 수렴해도 AI 비용은 수렴하지 않는다**(내가 "멱등이라 하루 1회"라고 한 게 틀렸다). 뱅크 전용으로 좁히니 두 사유가 소멸 → 번복이 아니라 **범위 축소**. `design-change-procedure` 절차로 **D-005 신설**(메타 없던 결정이라 1단계 "없으면 만든다"), 기각 목록에 취소선+근거표, `verify` 내용 단언 3개로 전제를 잠금. 🔴 **QA F-6 — 내 실수**: 마커 3개 넣고 "반증 확인"이라 썼는데 **1개만 시험했고**, 그중 하나(`SecurityConfig ~ daily-question`)는 그 문자열이 `permitAll` 매처에 이미 있어 **트리비얼하게 항상 참**이었다. → `WebMvcConfig`로 이전 + **"검사기는 존재만 단언하고 부재는 못 한다"는 한계를 각주로 명시** + 3개 전부 개별 반증. **반쪽 가드를 완전한 가드로 적는 게 가드 없는 것보다 나쁘다.** QA 6건 전건 종결(fixed 4·deferred 2), 원장 L-26(OSIV 동시성)·L-27(Port 미주입 2번째)·L-28(웹이 메일보다 9h 빠름) 등재 — **셋 다 트리거 명시**. | 2026-08-18 |
| **#386** | **★ 하네스 동결 — 죽은 코드 214줄 삭제 + 원장 하네스 항목 전건 종결 (08-17, 비용 $0).** 사용자 지적(*"기능이 아닌 하네스에만 시간을 쓰는 것 같다"*)이 옳았다. **8월 실측: 커밋 31건 중 하네스 18건(58%) vs 제품 7건(23%)**, 마지막 제품 기능은 #361(08-06). 그 뒤는 하네스가 자기 사고를 수습하는 사슬이었다 — #377(훅 12개 mode 644 수리) → 되살아난 가드가 위임을 통째로 차단 → #383(수습) → 그 수습이 만든 오탐·fail-open → #384(수습 + 스위트 152건), 그리고 **#384에서 처리한 20건 중 4건은 수정 과정에서 새로 만든 것**이었다. **삭제**: `qa-effect-guard.sh` 170줄 + 테스트 44줄. 08-16에 *"배선만 빼고 남긴다"* 고 했으나 그 결정이 남긴 것은 **한 번도 실행된 적 없는 코드 + 원장 L-24 + 배선 + 주석 4곳**이었다 — "언젠가 재배선"은 유지비를 지금 내고 편익을 미래로 미루는 거래다. `MIN_CASES` 152→143. 🔴 **정리 브랜치를 만들자마자 새 결함이 드러났다**: `qa-gate-test.sh`가 **주변 브랜치명에 좌우**됐다 — 같은 커밋에서 `chore/harness-trim` **3/9**, `tmp/x` 9/9. `assert-qa-run.sh`가 `chore/`·`docs/`를 면제하는데 테스트는 **detached일 때만** 임시 브랜치를 만들었다(08-16 CI 대응의 반쪽 땜질). CI(detached)도 `fix/*`도 초록이라 아무도 몰랐다. → 항상 임시 브랜치. **교훈: 테스트가 환경의 값을 읽는다면 그 값을 테스트가 소유해야 한다. "특정 상황에서만 격리"는 격리가 아니다.** **원장 13→9, 하네스 항목 0건**: L-24 closed(삭제) · L-23 wontfix(얕은 방어가 의도대로 작동함을 이 PR이 실증 — 하한이 걸렸고 근거를 적게 만들었다) · L-22 wontfix 유지 **+ 정정**(근거로 들었던 완화책 `WATCH`가 사라졌음을 명시 — *한쪽을 지우고 그것을 근거로 삼던 문서를 안 고치는* 반복 패턴) · L-18 wontfix(트리거 4/20 도달했으나 게이트 완화는 3번 시도해 3번 구멍을 냈다). **`CLAUDE.md`에 하네스 동결 규칙 신설** — `.claude/`·`.github/` 작업은 *"제품 작업이 이것 때문에 막혔다"* 는 증거가 있을 때만. 이론적 구멍은 착수 사유가 아니다. | 2026-08-17 |
| **#355** | **★ 관측 설정 부재 시 앱이 죽던 문제 — 스위치를 플래그에서 값 존재로 (08-03, 비용 $0, prod 배포·`/health` 200 확인).** #349 발견 ①의 후속. `OtlpMetricsConfig`의 가드가 **"켜라고 했는가"**(`grafana.otlp.enabled`, prod yml에 하드코딩 `true`)만 보고 **"쓸 수 있는가"**(키 존재)를 안 봐서, 기본값 없는 `@Value("${GRAFANA_API_KEY}")`가 `PlaceholderResolutionException`으로 부팅을 막았다. 로깅 경로는 `5cf76da`에서 이미 `defaultValue=""`로 같은 형태를 갖췄는데 **메트릭만 빠져 있던 비대칭**이라 그쪽에 맞췄다(선택지 ①`GRAFANA_OTLP_ENABLED` 환경변수화는 **노브가 2개가 되어 서로 어긋날 여지**가 있어 기각). 🔴 **조사 중 더 나쁜 것을 발견**: `secrets.tf`가 주입하던 자리표시가 **비어있지 않다는 사실만으로** 두 관측 경로가 켜져 있었다 — 더미 키 + `application-prod.yml`의 **진짜** instance-id `1680166` 조합으로 **학습 클러스터가 실제 Grafana Cloud를 60초마다 두드렸고**(원칙을 키에만 지키고 인스턴스 ID로 샜다), `GRAFANA_LOKI_URL=127.0.0.1:3100`은 logback `length()>0`을 통과시켜 어펜더를 붙였다. → 자리표시 3종 **삭제**(빈 문자열로 두지 않았다 — 존재 목적이 "비어있는 것"인 변수는 잘못된 멘탈모델을 만든다). **부재가 곧 스위치**라는 점에서 #353의 *"태그를 안 붙이는 것이 자물쇠"* 와 같은 모양. 🔴 **같은 병 3연발**: `secrets.tf`가 *"로깅 3개는 여기서 주입하지 않는다"* 고 **적어놓고 아래에서 주입** · QA **F-1** 내가 만든 안전장치가 빈 SHA에서 `git show ":path"`로 **로컬 인덱스를 읽어 ✅** · **F-3** F-1 수정에 붙인 `2>/dev/null`이 `set -e`에서 **무출력 사망**. 전부 *"통과했다고 믿게 만드는 검사"*. F-3은 가드를 얹지 않고 **내가 추가한 것을 도로 뺐고**, 네 번째 결함(ECR 태그가 PR 머지 커밋이라 조회 불가 → 오탐)에서는 **손을 멈추고 사용자에게 물었다**(빨간 깃발 *"3번 시도했는데 4번 더"*). 결론은 **코드 0줄 변경 — 🔴를 "추적 불가능한 이미지는 유료 세션에 쓰지 않는다"는 정책으로 재정의**. F-2(도달 불가 `require()` 2줄)는 `wontfix` — 죽은 *설정*과 달리 **불변식 assert**다. 모듈 최초 테스트 4개. QA가 **`PropertyPlaceholderAutoConfiguration` 없으면 `${...}`가 해석 안 돼 거짓 GREEN**임을 직접 재현해 확인. | 2026-08-03 |
| **#351 · #352 · #353** | **★ 영속 리소스 트랙 개설 — 가드레일 → CONTEXT 자기참조 제거 → Stage 3b (07-31, 비용 $0, 머지 시 EBS 월 $0.91 개시).** 사용자 지시(*"문제를 피하기보단 어떻게 방지·추적·모니터링하는지가 더 중요"*)로 **볼륨을 만들기 전에 관리 장치부터** 세웠다. **#351**: 원장 `PERSISTENT-RESOURCES.md`(각 항목에 증가 상한 필수) · SOP 고아검사를 **성격이 반대인 두 검사로 분리**(9=0건이어야 / 9b=원장과 일치해야 — 영속 볼륨은 destroy 후 영원히 available이라 기존 쿼리로는 매 세션 오탐이고 **매번 실패하는 검사는 곧 무시된다**) · 예산을 월간→**누적 크레딧 $10 단위 20단계**(월간은 리셋돼 실사용 월 $14에선 $10만 반복 발동) · 세션 배너 · 리퍼 고아 경고(감지 전용 — 생존판정에 넣으면 무한 destroy 루프). **#352**: 사용자 지적(*"main의 CONTEXT가 자꾸 안 맞는다, 매번 이래"*)이 옳았고 **원인은 성실성이 아니라 자기참조**였다 — PR은 자기 머지 사실을 못 담아 `머지 대기`가 머지 순간 확정적으로 거짓이 된다. **클린 클로즈 PR 24건**이 증거(git log 실측). `브랜치`·`열린 PR` 필드 삭제 + `session-status.sh` 신설로 **파생 가능한 상태 저장을 중단**(QA F-2 "$200 변수 중복"과 같은 병, blast radius만 다름). **#353**: 3a 동적 PVC → terraform 소유 영속 EBS + static PV. **원 설계에 없던 확정 2건** — ①EBS를 `0-bootstrap`에(2-cluster면 리퍼가 6개월 데이터를 자동 삭제. `prevent_destroy`도 답이 아니다: destroy **전체**가 거부돼 안전장치가 벽돌이 된다 — `local_file` 3개로 $0 재현) ②**노드 AZ 고정**(원 설계에 AZ 얘기가 없었다. 3a는 `WaitForFirstConsumer`가 가려주고 있었을 뿐이라 그대로 갔으면 **50% 확률로만 터지는** 버그). 🔴 **실측 5건**: 예산당 알림 **10개 총계 하드 리밋**(plan이 못 잡는 4번째 함정 → `chunklist`로 구조 차단) · **알림 예산 무료**(Pricing API, "첫 2개 무료 후 $0.02/일"은 옛 모델) · ANNUALLY+과거 시작일 수용 · **누적 소진 $0.481/$200** · `AmazonEBSCSIDriverPolicy` v15에서 **Attach는 무조건부·Delete만 태그 조건** → **태그를 안 붙이는 것이 곧 자물쇠**(IAM은 기본이 거부라 태그는 삭제를 *켜는* 스위치다). 🔴 **내 코드 결함을 반증 테스트가 5번 잡았다**: 고아 필터가 키 존재만 봐서 `Persistent=false`·대문자 `True`가 통과 · `elif [ $? -eq 0 ]`이 앞 test 결과를 읽어 **AWS 조회 실패가 "볼륨 0개"로 표시**될 뻔 · CSI 태그 검사기가 F-3 중괄호→F-4 computed key→F-5 줄단위 제외→F-6 `.tfvars`로 **네 번 뚫렸다**. 네 번째에서 멈추고(빨간 깃발 *"3번 시도했는데 4번 더"*) **역할을 재정의**했다 — tripwire이지 보안 경계가 아니다. F-7(런타임 조립)은 원장 L-12, 대신 **우회 불가능한 실물 조회**를 추가. **퀴즈 1차 0/5** — 다섯 문제 전부 *겉보기와 실제가 다른* 유형. 교정 후 확보: AZ의 확률적 실패 · `Persistent`는 우리 태그 · `prevent_destroy` 전체 실패 · **PV는 etcd에 산다**(`cluster-scoped`를 "클러스터 밖"으로 읽던 오개념 해소). 사용자 요청으로 **퀴즈 출제 형식 8규칙 고정**. QA 4라운드: HIGH 0 · F-1~F-6 fixed · F-7→L-12. | 2026-07-31 |
| **#388** | **★ L-26 해소 — daily-question 읽기 경로를 실제 Postgres 에서 검증 (08-18, 비용 $0).** #387 에서 미룬 L-26 의 트리거가 *"Stage B 착수 전 선행조건"* 이었고 다음이 Stage B 라 **그날이 만기**였다 — 트리거 도달했는데 또 미루면 원장이 장식이 된다. 검증 안 돼 있던 것: `open-in-view` 가 be 전체 0건 = Boot 기본값 `true` 라, **스케줄러 스레드**(EM 바인딩 없음) 전제로 설계된 `saveWithUniqueRecovery` 가 #387 로 **처음 웹 스레드**(OSIV 가 EM 바인딩)에서 돈다. 🔴 **겨냥한 위험**: Postgres 는 트랜잭션 안에서 한 문장이 실패하면 **전체가 abort** 되고 이후 쿼리가 죽는다 — **H2 는 안 그래서** 기존 테스트로는 볼 수 없었다. 🔑 **경합을 확률이 아니라 결정적으로 재현**: `CountDownLatch`+N스레드는 경합이 안 나도 초록이 뜬다 → `DailyQuestionContentPort` 데코레이터를 `@Primary` 로 주입해 `findExisting` 재확인 후 · `save()` 직전에 **별도 autocommit 커넥션**으로 오늘 행 INSERT. 매 실행 100% 재현. `@SpringBootTest(MOCK)`+`@AutoConfigureMockMvc` 라 OSIV 인터셉터가 실제로 붙는다. **프로덕션 코드 0줄 변경.** ✅ **가설 검증**: `SimpleJpaRepository.save()` 의 abort 가 자체 트랜잭션에 갇혀 재조회가 `current transaction is aborted` 없이 성공. 응답이 **경합 승자의 질문**과 일치 + 행 1개 + `ListAppender` 로 `log.warn` 관측(콘솔 grep 아님). **반증**: 경합 주입을 끄면 FAIL — 단언이 공허하지 않다. **CI 실행도 양성 확인**(테스트 리포트 아티팩트에서 2 tests/0 failures/0 ignored — 콘솔에 postgres 가 안 보인 건 Gradle 이 로그를 안 흘려서지 건너뛴 게 아니다). QA 가 의심 4건 전부 코드 추적 확인(주입 지점·`V13:10` UNIQUE 제약 실재·컨텍스트 캐시 격리·컨테이너 정리) + 보너스로 전 구간 `@Transactional` 부재까지. 원장: **L-26 closed**, L-30 신규(`ddl-auto: validate` 태우는 테스트 0건 — 기존 공백), **L-31 신규**(`assert-qa-run.sh` 의 SHA 비교가 정규화를 안 해 short SHA 로 막힘 — **이 PR 진행 중 실제로 당했다**. 훅을 고치면 그 커밋이 또 마커를 무효화하는 순환이라 미룸). | 2026-08-18 |
| **#391** | **★ Phase 2 Stage A 완료 — prod 실측 확인 (08-21, 비용 $0).** `GET /api/v1/daily-question` 이 **05:49 KST 에 200** — 09:00 cron 보다 **3시간 11분 먼저**다. 그 시점엔 오늘 행이 존재할 수 없으므로 **그 요청 자체가 lazy 생성을 유발했다**(08:17 재확인 시 같은 질문 = 멱등). 🔑 **사람이 아니라 기계가 찍었다** — 관찰 창이 00:00~09:00 뿐인데 08-18·19·20 **사흘 연속 놓쳤다**(그 시간에 작업을 안 하니까). `prod-smoke-daily.yml`(05:23 KST) 로 자동화하고 세션 크론을 백업으로 걸었다. **사람 규율에 맡기면 계속 놓친다는 게 실측으로 증명된 사례.** 완료 기준 3개 대조: ①`build` 497건 그린 ②prod 200 실측 ③읽기 경로 3파일에 유저·메일 참조 **0건**. ⚠️ ③은 **prod 에서 유저 0명을 관측한 게 아니라** 코드 경로가 무관함을 보인 것 — 구분해 기록했다. 부수 검증: 어제 11:06 수동 실행 때 뜨던 `::warning::`(09:00 이후 경고)이 05:49 실행엔 **안 떴다** — 시각 판정이 실제로 구분한다. ⚠️ 워크플로는 **남긴다** — 일회성 확인이 아니라 **상시 회귀 가드**다(#387 이전 상태로 되돌아가면 잡는다). | 2026-08-21 |
| **#392 · #393** | **★ Phase 2 Stage B 라이브러리 추출 2건 (08-22·08-26, 비용 $0).** G-1(D-006) 귀결 — Fly 가 계속 core-api 를 쓰므로 *"core 에서 daily 코드를 제거"* 하는 단계가 **영영 오지 않는다.** 앱 모듈로 빼면 구현이 두 벌 → **라이브러리로 뺀다(한 벌의 구현, 두 조립).** **#392 `core:daily-core`**: 착수 조사에서 **진짜 난이도가 daily 로직이 아니라 웹/에러 배관**임이 드러났다 — `DailyQuestionContentService` 는 레포 내부 import 가 전부 `core-domain`(결합 0건, KDoc 불변식이 실제로 지켜졌다)인데 `DailyQuestionService`(`CoreException`)·컨트롤러(`ApiResponse`)·레이트리밋(`ErrorCode`)이 묶여 있었다. **사용자 결정: 라이브러리는 순수 유지**(배관 불가침) → 컨트롤러는 각 조립 소유. **원장 L-27 트리거대로 해소**(`DailyQuestionGeneratorPort` 신설). **#393 `clients:client-ai-http`**: `daily-api` 가 부팅하려면 `TechInterviewPort` 빈이 필요한데 **HTTP 어댑터가 core-api 안에 있었다.** ⚠️ **내가 범위를 10배 오판했다** — *"import 가 core-domain 뿐이니 어댑터 하나만"* 이라 했으나 **상속(`BaseAiHttpAdapter`)을 안 봤다.** 실제 43파일 → B-2a/B-2b 로 분할. 🔑 **산출물 불변식을 "개수"가 아니라 "단순이름 1:1 대응"으로 걸었다** — *하나 지우고 다른 걸 추가* 도 개수는 통과하기 때문. 실제로 1쌍이 걸렸고(Kotlin `<파일명>Kt` 파사드 — **불변식이 깨진 게 아니라 못 본 형태**) builder 가 먼저 신고했다. QA 가 이동 40파일을 **바이트 단위 대조**해 순수 이동 확인. 산출물 334 불변 · be 509 tests 0 failures. 🔴 **#392 에서 설계 무결성 검사가 다른 전부(빌드·509테스트·jar 대조·QA)가 놓친 것을 잡았다** — 파일이 이동했는데 **D-005 영향 경로·verify 마커가 안 따라왔다.** 07-29 에 적어둔 *"코드 파일 삭제·이동 → 검사기가 경로를 잡음"* 이 한 달 뒤 발동. 그 경험을 #393 빌더 프롬프트에 넣었더니 이번엔 미리 돌려 깨끗했다 — **사고가 다음 PR 의 체크리스트가 됐다.** 원장: L-27 closed · L-33(Spring 예외 누수) · L-34(split package) · L-35(QA 판정 어휘 vs triage 상태 어휘 충돌) 신설 | 2026-08-26 |
