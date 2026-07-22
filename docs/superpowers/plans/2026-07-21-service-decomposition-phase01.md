# 서비스 분해 Phase 0~1 구현 계획 — ai-service 추출

> **For agentic workers:** BE 에이전트(be-feature-builder) 위임 전제. 각 태스크는 TDD(테스트 먼저) +
> `verification-before-completion` 게이트. 스텝은 체크박스(`- [ ]`)로 추적.
> 상위 설계: `docs/superpowers/specs/2026-07-20-service-decomposition-design.md` (착수 전 필독).

**Goal:** 단일 Spring 앱의 AI 컴퓨트 레이어를 별도 배포 단위 `ai-api`(내부 전용 stateless)로 추출한다.
`core-domain`의 AI 포트는 그대로 두고, core 쪽 구현을 **in-process 어댑터 → HTTP 어댑터**로 피처플래그
전환한다. **완료 판정 = 피처플래그 ON(HTTP)에서 기존 AI 평가 결과가 분리 전과 parity**(같은 입력 →
같은 스키마·동등 응답), 플래그 OFF로 즉시 롤백 가능.

**Architecture:** 공유 모듈(`core-enum`·`core-domain`·`db-core`)은 유지. 새 앱 모듈 `ai-api`가
`core-domain`(포트·반환 data class) + `client-ai`(평가자 구현)를 의존하고 각 포트를 REST로 노출한다.
`core-api`는 `client-ai` 의존을 떼고, 포트를 **HTTP 클라이언트 어댑터**로 구현한다. 계약(요청/응답
스키마)은 양쪽이 공유하는 `core-domain` data class로 정의 → 계약 드리프트 방지.

**Tech Stack:** Kotlin / Spring Boot 4.x, Spring AI(Anthropic), Gradle 멀티모듈, `RestClient`(HTTP 어댑터),
피처플래그(`@ConditionalOnProperty` 기반), JUnit5 + MockMvc.

---

## ⚠️ 이 계획의 근거 — Blindspot Pass 진단 (2026-07-21)

설계의 "AI 경계가 `*EvaluatorPort`로 깨끗이 존재, 어댑터만 in-process→HTTP 교체" 가정을 실제 코드와
대조한 결과 **부분 일치**. 아래 4개 불일치가 그대로 Phase 0의 선행 태스크가 된다. (직렬화·스트리밍은
걸림돌 없음 확인 — 전부 동기 blocking + 순수 data class 반환이라 HTTP 매핑 자체는 깨끗.)

| # | 불일치 | 확도 | 대응 태스크 |
|---|--------|:----:|-----------|
| 1 | **숨은 역결합**: `CacheMetricsAdvisor`가 매 AI 호출마다 `AiCallLogPort.record()` → 유일 구현이 `db-core/AiCallLogAdapter`(**core DB 쓰기**). ai-api로 뽑으면 이 빈이 없어 기동/호출 실패 | 🔴 | **Task 0.2** (방침) → **Task 1.3** (구현) |
| 2 | **포트 식별 불가**: AI(LLM) 포트 **17개** 중 7개가 `*EvaluatorPort` 접미사 미준수(`TechInterviewPort`·`InterviewCoachPort`·`CodingHintPort`·`SkillAssessmentPort`·`JourneyReportPort`·`ActClearReportPort`·`CodingProblemGeneratorPort`) → DB 영속성 포트와 같은 `port/` 패키지 혼재. 기계적 추출 시 누락 | 🔴 | **Task 0.1** |
| 3 | **설정 분산**: `devquest.ai.max-retry`·Judge0 설정이 `client-ai`가 아니라 `core-api/application.yml`에 있음 → 모듈만 옮기면 반쪽 이관 | 🟡 | **Task 1.2** |
| 4 | **트랜잭션 경계**: `AiCheckService` 등이 `@Transactional` 안에서 AI 포트+DB 포트 혼용 → HTTP 지연·부분 실패가 DB 트랜잭션에 유입 | 🟡 | **Task 1.4** |

**포트 인벤토리(확정, 17 LLM + 1 Judge0):** Blog·Resume·Essay·Interview·Personality·SystemDesign·
CompanyFit·JdAnalysis·BossPackage·DeveloperClass(=`*EvaluatorPort` 10) + CodingProblemGenerator·
CodingHint·SkillAssessment·JourneyReport·ActClearReport·InterviewCoach·TechInterview(=`*Port` 7) + `Judge0Port`.
구현체는 전부 `client-ai/.../evaluator/`의 `@Component`, 공통 부모 `BaseAiEvaluator`.

---

## Global Constraints

- **TDD**: 각 태스크 = 실패 테스트 먼저 → 최소 구현 → 리팩토링. "간단해서 생략" 금지.
- **헥사고날 규칙 유지** (CLAUDE.md 거부 규칙): `core-domain`에 Spring 어노테이션 금지 / `db-core`↔`client-ai`
  직접 의존 금지 / `!!` 금지 / Evaluator 테스트는 `@Mock`/`@InjectMocks` 대신 `RETURNS_DEEP_STUBS`.
- **무행동 변경(Phase 0)**: Phase 0의 모든 커밋은 기존 동작을 1비트도 바꾸지 않는다. 피처플래그 기본값 =
  **in-process**(OFF). 기존 테스트 전부 그린 유지 = Phase 0 완료 판정.
- **롤백 가능 (불변식)**: 피처플래그(`devquest.ai.transport=inprocess|http`)로 언제든 in-process 복귀.
  Phase 1 머지 후에도 프로덕션 기본값은 검증 완료까지 in-process. **이 롤백이 살아있으려면 core-api가
  `client-ai`를 계속 classpath에 유지해야 한다** — inprocess 경로가 client-ai 빈을 직접 쓰기 때문.
  ⚠️ **`client-ai` 의존 제거는 Phase 1/2 범위 밖**(http를 프로덕션 기본값으로 확정하고 충분히 검증한
  Phase 3 무렵에만). Phase 1에서 떼면 롤백이 죽는다.
- **Fly 배포 무영향 (불변식)**: 현 prod 배포는 `be/Dockerfile`이 **`./gradlew :core:core-api:bootJar`
  단독 빌드 → core-api jar만 실행**. `ai-api` 모듈을 추가해도 core-api가 의존하지 않으면 이 빌드 그래프에
  안 들어가 배포에 무영향. **Phase 0~1 내내 `core-api`의 배포 산출물(bootJar 실행 동작)은 불변** —
  main 머지가 Fly를 깨지 않는다. Fly에 3서비스를 올리는 것은 별개 선택(이 계획 범위 밖).
- **시크릿 안전**: `core-api/src/main/resources/application-local.yml`에 **평문 실제 키**(Anthropic·Judge0·
  Resend·Grafana) 존재 → **이 파일을 복제·이동·출력 금지**. 새 모듈 설정은 `${ENV:}` 플레이스홀더만.
- **계약 단일 출처**: HTTP 요청/응답 DTO를 새로 만들지 말고 **`core-domain`의 기존 반환 data class를 그대로
  직렬화**. 양쪽이 `core-domain`을 계속 의존 → 계약이 한 곳.
- **검증 루프**: 각 태스크 → 유닛/슬라이스 테스트 그린 → `./gradlew :<module>:test` → 커밋. Phase 경계마다
  전체 빌드 `./gradlew build` + parity 스모크.

---

# Phase 0 — 준비 (무행동 변경)

목표: 코드가 실제로 추출 가능한 상태가 되도록 seam을 정리하되, **런타임 동작은 그대로**. 끝나면 앱은
여전히 단일 배포, in-process로 동작.

### Task 0.1: AI 포트 식별 정리 — 마커 인터페이스 (불일치 #2)

**문제:** AI 포트 17개가 DB 포트와 같은 `port/` 패키지에 이름 규칙 없이 혼재 → "AI 포트만 HTTP화" 대상을
기계적으로 특정할 수 없다.

**Files:**
- Create: `be/core/core-domain/src/main/kotlin/com/devquest/core/domain/port/ai/AiEvaluatorPort.kt` (마커 인터페이스)
- Edit: AI(LLM) 포트 17개 — `AiEvaluatorPort`를 상속하도록 표식(시그니처 변경 없음)
- Create: `be/core/core-domain/src/test/.../ArchAiPortConventionTest.kt` (규약 테스트)

**Interfaces:**
- `interface AiEvaluatorPort` — 빈 마커(순수 Kotlin, Spring 어노테이션 금지 준수).
- 17개 포트가 `: AiEvaluatorPort`를 붙임. 메서드 시그니처·반환 타입 불변.

**개념 노트:** 마커 인터페이스는 "이 포트는 AI 컴퓨트 경계에 속한다"는 **분류를 타입으로** 만든다.
패키지 대이동(`port/ai/`로 파일 이동)보다 침습이 적고 import 변경이 없어 Phase 0 무행동 원칙에 맞다.
규약 테스트가 "새 AI 포트가 마커를 안 붙이면 실패"하게 해 이후 드리프트를 막는다.

- [ ] **Step 1:** `ArchAiPortConventionTest` 작성(실패) — "AiEvaluatorPort 하위 = 정확히 17개, 목록 명시"
- [ ] **Step 2:** 마커 인터페이스 생성 + 17개 포트에 상속 표식 → 테스트 그린
- [ ] **Step 3:** `Judge0Port`는 **비-LLM**(코드 채점)이므로 마커에서 제외 — 별도 취급(Task 1.1에서 결정). 주석으로 명시
- [ ] **검증:** `./gradlew :core:core-domain:test` 그린, 기존 테스트 무변경

> **결정 필요(경미):** Judge0을 ai-api에 함께 넣을지(외부 RapidAPI 코드채점) vs core에 남길지.
> 추천 = **ai-api에 포함**(둘 다 "외부 컴퓨트 위임"이라 성격 일치, core를 순수하게). Task 1.1에서 확정.

### Task 0.2: AI 호출 로깅 역결합 해소 — 방침 확정 (불일치 #1, High)

**문제:** `client-ai/support/CacheMetricsAdvisor`가 모든 `ChatClient`에 등록돼 매 호출마다
`aiCallLogPort.record(AiCallLog)` 실행. 유일 구현이 `db-core/AiCallLogAdapter`(core DB INSERT).
ai-api엔 db-core 빈이 없다 → 그대로 뽑으면 **기동 시 `AiCallLogPort` 빈 없음 / 호출 시 NPE**.

**방침 확정 (2026-07-21, 사용자 결정): A — 관측으로 재배치.** ai-api가 `AiCallLogPort`를 **자체 관측
어댑터**로 구현(OTLP 메트릭·구조화 로그), core DB의 `AiCallLog` 테이블 write는 ai-api 경로에서 제거.
관측 관심사를 서비스 소유로 정렬 = 결합 0.
**조건(반드시 조사로 검증): core에 `AiCallLog`를 읽는 소비처가 없어야 한다.** 있으면 데이터 출처가
이동하므로 → **C 병행**(응답 메타를 HTTP 응답에 실어 core가 기록)으로 보정.

| 안 | 내용 | 트레이드오프 |
|----|------|------------|
| **A ✅확정** | ai-api가 `AiCallLogPort`를 관측 어댑터로 구현. core DB write 제거 | 결합 0·최고로 깨끗. 단 core에 소비처 있으면 출처 이동 |
| C (보정용) | 응답 메타(모델·토큰·지연)를 HTTP 응답에 실어 **core가** 기록 | core DB 유지. 소비처 존재 시 A와 병행 |
| B (기각) | ai-api→core HTTP 콜백 | 역방향 결합·부분 실패·순환 위험 |

- [x] **Step 1:** `AiCallLog` 테이블 **소비처 조사** (2026-07-21 완료, grep 전수) — **읽기 소비처 0건.**
- [x] **Step 2:** 결과 기록 → 하단 "결정 로그" 및 아래 확정 박스.
- [x] **검증:** 코드 변경 없음(조사·결정만). 근거 첨부 완료.

> **✅ 조사 결과 (2026-07-21) — A 단독 확정.** core에 `AiCallLog`를 읽는 소비처 **0건**:
> - `AiCallLogPort` = `record()` **write-only 단일 메서드** (읽기 메서드 없음).
> - `AiCallLogRepository`는 `JpaRepository` 상속만, 커스텀 `find/count/@Query`/집계 **0개**. 상속된
>   `findAll/count`도 **호출하는 코드 0** = 죽은 능력.
> - `AiCallLogPort` 주입처는 `client-ai/support/CacheMetricsAdvisor` **하나뿐**(write). 통계 화면·비용
>   API·대시보드·엔티티 조인 전부 없음.
> - 저장 필드: `evaluatorName·modelName·input/output/cacheRead/cacheCreationTokens·latencyMs·success`
>   (**비용 필드 없음** — 순수 관측 데이터). 엔티티 `AiCallLogEntity`(`ai_call_log` 테이블, V7 마이그레이션).
> - → **A+C 병행 불필요. ai-api가 자체 관측(OTLP)으로 재배치 + core DB write 제거해도 잃는 것 없음.**
> - **Task 1.3 구현 지침**: ai-api에 `AiCallLogPort` 관측 어댑터 구현. **core의 db-core write 경로
>   (`AiCallLogAdapter.record`)는 ai-api 호출 경로에서 더는 안 탐.** `ai_call_log` 테이블·db-core
>   어댑터의 **물리적 삭제는 Phase 3 정리 대상**(inprocess 롤백 유지 동안은 남겨둠 — Phase 1/2에서
>   inprocess 경로는 여전히 이 write를 쓰므로 제거 금지).

> **이 태스크는 조사·결정.** 코드 변경은 Phase 1. Phase 0 무행동 원칙 유지.

### Task 0.3: `ai-api` Gradle 앱 모듈 스캐폴드

**Files (실제 구현 #298 기준):**
- Edit: `be/settings.gradle.kts` — `include("core:ai-api")`
- Create: `be/core/ai-api/build.gradle.kts` — Spring Boot 앱, 의존 **`:core:core-domain`만** (+ web·actuator
  스타터). ⚠️ **`:clients:client-ai`는 이 태스크에서 의존하지 않음** — 아래 결정 박스 참조.
- Create: `be/core/ai-api/src/main/kotlin/com/devquest/ai/AiApiApplication.kt`
- Create: `be/core/ai-api/src/main/resources/application.yml` (포트 **8081**, actuator health, 시크릿 `${ENV:}`)
- Create: `be/core/ai-api/src/test/kotlin/com/devquest/ai/AiApiContextLoadTest.kt`

**Interfaces:**
- 독립 기동 가능한 Spring Boot 앱. 이 시점엔 **컨트롤러 없음**(빈 스캐폴드) — 아무도 호출 안 함.

- [x] **Step 1:** `AiApiContextLoadTest`(RED 확인 — initializationError)
- [x] **Step 2:** settings include + build.gradle.kts + Application.kt + application.yml → 컨텍스트 로드 그린
- [x] **Step 3:** `application-local.yml` 미생성(준수). 시크릿 플레이스홀더 불요(client-ai 미의존)
- [x] **검증:** `:core:ai-api:test` 그린 · `build` 전체 그린 · `:core:core-api:bootJar -x test` 성공(Fly 무영향)

> **✅ 구현 결정 (2026-07-21, #298 — Task 0.2 스타일 기록): client-ai 의존은 Phase 1로 연기.**
> client-ai를 이 스캐폴드에 붙이려 하자 실런타임 체인이 드러남:
> `AiClientConfig.chatClient()` → `@Component CacheMetricsAdvisor` → 생성자가 `AiCallLogPort` 요구
> → 유일 구현 `AiCallLogAdapter`가 **db-core**에 있음. 즉 client-ai를 붙이면 컨텍스트 로드에
> **db-core(DataSource·JPA)까지** 필요 → "빈 스캐폴드" 취지 위반. 이는 계획이 **Blindspot #1**로 이미
> 예측한 실패(Task 0.2에서 "A안, 구현은 Task 1.3"으로 결정한 그 결합)다. QA가 파일로 검증: 아키텍처
> 규칙 `client-ai↔db-core 직접 의존 금지` 위반 아님(Gradle 모듈 레벨 위반 없음, 런타임 빈 배선은
> Port/Adapter 설계상 정상 — 합성 루트 core-api가 배선). → **`core-domain`만 의존.** client-ai 의존 +
> 컨트롤러는 **Phase 1**에서, 단 **Task 1.3(ai-api용 `AiCallLogPort` 관측 어댑터)이 선행/동반이어야
> client-ai가 db-core 없이 뜬다** (아래 Phase 1 순서 노트 참조).
> - web·actuator 스타터 추가: actuator health를 HTTP로 노출하려면 web 스타터 필요 = 최소 필요(과함 아님).
> - 모듈 네이밍 `core:ai-api` 확정(core-api 형제). 최종 3앱: core-api·ai-api·daily-api.

### Task 0.4: HTTP 어댑터 + 피처플래그 (in-process 기본)

**Files:**
- Create: `be/core/core-api/.../adapter/ai/http/` — 각 AI 포트의 HTTP 구현체(뼈대, Task 1.4에서 배선)
- Create: `be/core/core-api/.../config/AiTransportConfig.kt` — `@ConditionalOnProperty(devquest.ai.transport)`
- Edit: `be/core/core-api/src/main/resources/application.yml` — `devquest.ai.transport: inprocess`(기본)

**Interfaces:**
- `devquest.ai.transport=inprocess` → 기존 `client-ai` 빈 사용(현행). `=http` → HTTP 어댑터 빈.
- 소비 서비스(`AiCheckService` 등)는 **포트만 주입** → 전환이 서비스 코드에 무영향(주입 대상 빈만 교체).

**개념 노트:** 피처플래그를 **빈 선택 레벨**에 두면(서비스는 인터페이스만 앎) strangler 전환이 설정 한 줄.
Phase 0에선 HTTP 빈이 아직 실제 호출을 안 하므로 기본값 inprocess로 동작 불변.

- [x] **Step 1:** 전환 스위치 슬라이스 테스트 2개(`AiTransportInprocessSwitchTest`/`HttpSwitchTest`) — 빈 주입 검증
- [x] **Step 2:** `AiTransportConfig`(`@Bean`+`@ConditionalOnProperty(http)`+`@Primary`) + `BlogHttpEvaluator`
  뼈대(`TODO()` 미배선). **client-ai·소비서비스 무수정** — 기본 inprocess면 HTTP 빈 미등록→client-ai 주입.
- [x] **검증:** core-api 161 tests 0 failures(무행동) · build 그린 · core-api bootJar 성공(Fly 무영향).

> **✅ 구현 결정 (2026-07-21, #300):** 전환 = `AiTransportConfig`의 조건부 `@Bean`+`@Primary`(어댑터 자체에
> @Component 안 달아 client-ai 무수정). HTTP 어댑터는 **대표 1개(BlogEvaluatorPort→`BlogHttpEvaluator`)만**
> 뼈대 구현 — 같은 패턴이 나머지 16개에 기계적 적용되므로 메커니즘 증명엔 1개로 충분. **나머지 16개는
> Phase 1 Task 1.4에서 실제 HTTP 배선과 함께.** 테스트는 `@SpringBootTest(webEnvironment=NONE)` 풀 컨텍스트
> 슬라이스(레포 선례 따름).

**✅ Phase 0 완료 (2026-07-21, Task 0.1~0.4 전부 머지 #295·#297·#298·#300).** 아래 판정 전부 충족.

**Phase 0 완료 판정:** `./gradlew build` 그린 · 기존 테스트 100% 그린 · 앱 런타임 동작 불변 · `ai-api` 모듈
독립 기동 · 피처플래그 기본 inprocess · **Fly 배포 무영향 검증(아래)**. **아직 단일 배포, HTTP 미사용.**

> **✅ Fly 배포 무영향 검증 (필수, 각 Phase 0 태스크 후):** `./gradlew :core:core-api:bootJar -x test`가
> **ai-api 모듈 없이도** 성공 = Fly Dockerfile이 빌드하는 산출물이 온전. core-api bootJar가 정상 기동
> (`transport` 미지정 시 inprocess 기본). ai-api를 settings에 include해도 core-api 빌드 그래프는 불변임을
> 이 명령으로 증거화. **main 머지 전 이 검증 없이 Phase 0 커밋 금지.**

---

# Phase 1 — ai-service 추출 (HTTP 전환 + parity)

목표: `ai-api`가 AI 포트를 REST로 노출하고, core가 HTTP 어댑터로 이를 호출. 피처플래그 `http`에서
기존 결과와 parity 확인.

> **⚠️ Phase 1 태스크 순서 (Task 0.3 #298에서 확정된 의존):** `Task 1.1`에서 `client-ai`를 ai-api에
> 붙이는 순간 `CacheMetricsAdvisor`가 `AiCallLogPort` 빈을 요구한다. ai-api엔 db-core가 없으므로 이
> 빈은 **`Task 1.3`(ai-api용 `AiCallLogPort` 관측 어댑터)이 제공**해야 컨텍스트가 뜬다. 따라서 실행 순서는
> **1.3(또는 1.3의 최소 어댑터) → 1.1 → 1.2 → 1.4**. 즉 **1.3을 1.1의 선행/동반으로 당긴다.** (문서상
> 번호는 유지하되 착수 순서만 1.3 먼저.) 이걸 놓치면 Task 0.3이 겪은 "빈 없음" 실패가 Task 1.1에서 재현된다.
>
> **+ Task 1.1 착수 시 (Task 0.3 LOW 메모):** `AiApiApplication`의 컴포넌트 스캔이 현재 `com.devquest.ai`로
> 한정 → client-ai(`com.devquest.client.ai.*`) 빈을 잡으려면 `scanBasePackages = ["com.devquest"]`로
> 넓혀야 함(core-api의 `DevQuestApplication`과 동일 패턴).

### Task 1.1: ai-api REST 컨트롤러 — 포트를 HTTP로 노출 (⚠️ Task 1.3 선행)

**Files:**
- Create: `be/core/ai-api/.../controller/` — 포트별(또는 도메인 묶음별) 컨트롤러, `client-ai` 평가자 주입
- Create: 각 컨트롤러 MockMvc 슬라이스 테스트

**Interfaces:**
- `POST /internal/ai/{evaluator}/{action}` 규약(예: `/internal/ai/blog/evaluate`). 요청 body =
  포트 파라미터를 감싼 요청 객체, 응답 body = **core-domain 반환 data class 그대로**(계약 단일 출처).
- Kotlin default 파라미터(`resumeContent=""`·`recentQuestions=emptyList()`·`modelAnswer=null`)는 HTTP에서
  소실 → **요청 DTO에서 nullable + 서버측 기본값 복원** 명시(불일치 대응).

**개념 노트:** 컨트롤러가 곧 "포트의 HTTP 표현". 포트 하나당 얇은 엔드포인트로 매핑하면 core의 HTTP
어댑터와 1:1 대응이라 parity 추적이 쉽다. `InterviewCoach`·`TechInterview`처럼 다단계 포트도 서버 세션이
없고 히스토리를 파라미터로 받으므로(진단 확인) 각 호출을 독립 엔드포인트로 낼 수 있다.

- [x] **Step 0 (선행, #304 QA 이월):** 어댑터의 `ai.call.log.latency` 타이머 **제거**
      (`ai.call.duration`과 동일 값 중복 — Task 1.3 결정 박스 참조). 카운터는 유지.
- [x] **Step 1:** 포트별 컨트롤러 테스트(실패) — 요청→평가자 위임→data class 직렬화 검증
- [x] **Step 2:** 컨트롤러 구현. Judge0은 Task 0.1 결정대로 포함
- [x] **Step 3:** default 파라미터 소실 케이스 테스트(생략 필드 → 서버 기본값 복원)
- [x] **검증:** `./gradlew :core:ai-api:test` 그린 + `:core:core-api:bootJar -x test` 성공(Fly 무영향)

> **✅ 구현 완료 (2026-07-22, #305 — QA HIGH 1건·MEDIUM 3건 후속 처리 포함).**
> - **엔드포인트 24개** — 18개 컨트롤러(17개 `AiEvaluatorPort` + `Judge0Port`)에 걸쳐 `/internal/ai/{evaluator}/{action}`
>   규약으로 노출. Judge0은 Task 0.1 추천대로 **포함**(비-LLM이지만 "외부 컴퓨트 위임" 성격 동일).
> - **default 파라미터 소실 대응 3건**: ①`JdAnalysisRequests.resumeContent`(nullable + `?: ""` 복원)
>   ②`TechInterviewDailyQuestionRequest.recentQuestions`(nullable + `?: emptyList()` 복원)
>   ③`TechInterviewExplainFollowupRequest.modelAnswer`(도메인 포트 자체가 `String? = null`이라 HTTP
>   필드 생략 시 자연히 null — 별도 복원 로직 불필요, 자연 케이스로 3번째 유형에 포함).
> - **String 반환 엔드포인트 wire 계약 실측 확정 (QA HIGH 후속, `TechInterviewWireFormatContractTest`):**
>   `/daily-question`·`/explain-followup`은 `produces = APPLICATION_JSON_VALUE`를 붙여도 반환 타입이
>   순수 `String`이라 `StringHttpMessageConverter`가 먼저 선택되어 **따옴표 없는 raw text**가 나감
>   (헤더는 `application/json`인데 바디는 유효한 JSON이 아닌 불일치 상태였음 — QA 가설 확인됨).
>   → `produces = "text/plain;charset=UTF-8"`로 정정해 헤더·바디를 일치시킴. 한글 인코딩은 실측상
>   UTF-8로 정상(Boot 전체 컨텍스트가 `StringHttpMessageConverter` 기본 charset을 재구성). Task 1.4의
>   RestClient는 이 두 엔드포인트를 `text/plain` 응답으로 취급해 `String` 그대로 읽으면 됨.
> - **에러 경로 관측(MEDIUM, 설계 결정 아님·Task 1.4용 기록):** 포트가 런타임 예외를 던지면 Spring Boot
>   기본 에러 핸들링(`BasicErrorController`)이 동작 — 상태코드 그대로(예: 500) +
>   `{"timestamp","status","error","path"}` 형태 JSON(‌RFC 7807 `ProblemDetail` 아님). Kotlin non-null
>   필드가 JSON에서 누락되면 `HttpMessageNotReadableException` → **400**, 동일 바디 형태.
>   `ApiControllerAdvice`는 core-api 모듈 소유라 ai-api엔 없음 — 에러 핸들링 커스터마이즈는 Task 1.4 설계 몫.

### Task 1.2: 설정 이관 (불일치 #3)

**Files:**
- Edit: `be/core/ai-api/src/main/resources/application.yml` — `devquest.ai.max-retry` + `devquest.judge0.*` 추가
  (`spring.config.import: classpath:client-ai-anthropic.yml`는 Task 1.1에서 이미 추가됨)
- Edit: `be/core/core-api/src/main/resources/application.yml` — **키 삭제 없음.** 소유·이관 상태를 설명하는
  주석만 추가(아래 결정 박스 참조)
- Create: `be/core/ai-api/src/test/kotlin/com/devquest/ai/config/AiConfigBindingTest.kt` — 런타임 바인딩 검증

**개념 노트:** `AiCallExecutor`(max-retry 소비)·`Judge0Adapter`(judge0 설정 소비)가 ai-api로 이동하므로
설정도 따라와야 한다. `client-ai-anthropic.yml`은 client-ai 모듈 리소스라 자동 동행, 문제는 **core-api에
흩어져 있던 키**뿐.

- [x] **Step 1:** ai-api 기동 시 max-retry·judge0 설정 바인딩 테스트(`AiConfigBindingTest`) — RED 확인(3/4
  실패: `Environment.getProperty`가 null, `@Value` 기본값은 Environment에 등록되지 않으므로 우연한 기본값
  일치가 아니라 명시적 선언 여부를 정확히 가른다) → 이관 후 GREEN.
- [x] **Step 2:** 설정 이관, `${ENV:}` 유지. 실제 키 하드코딩 금지. `boss-model`/`boss-max-tokens`는
  `client-ai-anthropic.yml` 공유 import로 이미 동행 확인(이관 불필요, 회귀 테스트로만 고정).
- [x] **검증:** `:core:ai-api:test` 41 tests 0 failures · `:core:core-api:test` 161 tests 0 failures(inprocess
  불변 증거) · `build` 그린 · `:core:core-api:bootJar -x test` 성공(Fly 무영향).

> **✅ 조사 결과 (2026-07-22) — 소비 주체 전수 확인.** `client-ai/src/main/`에서 `@Value` 5건,
> `@ConfigurationProperties` 0건:
>
> | 키 | 소비 클래스 | core-api yml 값 | 코드 기본값 | ai-api 조치 |
> |----|-----------|----------------|-----------|-----------|
> | `devquest.ai.max-retry` | `AiCallExecutor` | `3` | `3` | 명시 추가(동일 값) |
> | `devquest.judge0.api-key` | `Judge0Adapter` | `${JUDGE0_API_KEY:}` | `""` | `${JUDGE0_API_KEY:}` 플레이스홀더로 추가 |
> | `devquest.judge0.api-host` | `Judge0Adapter` | `judge0-ce.p.rapidapi.com` | `judge0-ce.p.rapidapi.com` | 명시 추가(동일 값) |
> | `devquest.ai.boss-model` | `AiClientConfig.bossChatClient` | (core-api yml에 없음, `client-ai-anthropic.yml`에 `claude-sonnet-4-6`) | `claude-sonnet-4-6` | **이관 불필요** — `client-ai-anthropic.yml`을 core-api·ai-api가 동일하게 import(Task 1.1에서 이미 완료)해 값이 파일 자체를 공유. 회귀 테스트로 고정만 함 |
> | `devquest.ai.boss-max-tokens` | `AiClientConfig.bossChatClient` | (위와 동일) `4000` | `4000` | 위와 동일 |
>
> `devquest.ai.pass-score`(70)·`devquest.ai.interview-questions`(10)은 **전체 `be/` 소스에 `@Value`/
> `@ConfigurationProperties` 소비처 0건**(사용되지 않는 선언). client-ai가 안 쓰므로 이 태스크 범위 밖 —
> core-api에서 손대지 않음(삭제도 이관도 하지 않음, 별개 정리 대상이면 이 계획 밖에서 판단).
>
> **core-api도 쓰는 키(= 절대 삭제 금지):**
> - `devquest.ai.transport` — `AiTransportConfig`가 `@ConditionalOnProperty(prefix="devquest.ai", name=["transport"])`로
>   **직접** 소비(core-api 고유, ai-api 무관). 근거: `be/core/core-api/.../config/AiTransportConfig.kt:32`.
> - `devquest.ai.max-retry`·`devquest.judge0.*` — core-api 코드가 `@Value`로 직접 참조하진 않지만,
>   **inprocess 모드에서 client-ai의 `@Component`(`AiCallExecutor`/`Judge0Adapter`)가 core-api Spring
>   컨텍스트 안에서 그대로 뜨므로** core-api 프로세스가 여전히 이 값을 필요로 한다(롤백 불변식). 근거:
>   Global Constraints "client-ai 의존 제거는 Phase 3" + `AiTransportConfig` 주석.
>
> **⚠️ 계획 원문 대비 판단 변경 (Deviation, 보수적 선택):** 원문 Files 절은 "core에 불필요분 제거"라고
> 돼 있었으나, 위 근거로 **core-api에서 어떤 키도 삭제하지 않기로 결정**했다. Phase 1 내내 core-api는
> `transport=inprocess` 기본값으로 client-ai 빈을 그대로 호스팅하므로, 지금 키를 지우면 inprocess 경로가
> 깨져 롤백 불변식(Global Constraints)을 위반한다. 대신 core-api yml에 "이 키는 ai-api로 이중화됐고
> Phase 3(client-ai 컴파일 의존 제거)에 core-api에서 삭제한다"는 **주석만** 남겼다. 이 문서 갱신이 그
> Deviation 기록이다.

### Task 1.3: AiCallLog 로깅 재배치 (Task 0.2 방침 구현)

**Files:** (Task 0.2에서 확정한 안에 따름 — 기본 가정 A)
- Create: `be/core/ai-api/.../adapter/AiCallLogObservabilityAdapter.kt` — `AiCallLogPort` 구현(메트릭/구조화 로그)
- (A안) core에서 AiCallLog DB write 경로가 ai 호출과 분리됐는지 확인

- [x] **Step 1:** ai-api `AiCallLogPort` 빈 존재 + 호출 시 예외 없음 테스트
- [x] **Step 2:** 방침 A대로 구현 — `AiCallLogObservabilityAdapter`(구조화 로그 + Micrometer, DB 접근 0)
- [x] **검증:** `DataSource` 빈 부재 단언으로 core DB 미의존 격리 증명 · `:core:ai-api:test` 7 tests 0 failures ·
  `build` 그린 · `:core:core-api:bootJar -x test` 성공(Fly 무영향) · BE CI 통과

> **✅ 구현 완료 (2026-07-22, #304).** 어댑터는 `MeterRegistry`만 주입받고 DB에 접근하지 않는다.
> 구조화 로그 1줄에 토큰 4종(input/output/cacheRead/cacheCreation)·latency·success를 전부 남겨
> **비용 추적 관측 공백 없음**. 메트릭은 `CacheMetricsAdvisor`의 기존 이름(`ai.call.duration`·
> `ai.tokens.*`)과 충돌하지 않도록 `ai.call.log.*` 네임스페이스로 분리.
> 예외 안전성은 `CacheMetricsAdvisor`가 `record()` 호출을 `runCatching`으로 감싸고 있어 확보됨
> (어댑터 내부 예외가 AI 응답 반환 경로를 깨지 않음).
>
> **⚠️ QA MEDIUM 2건 → Task 1.1에서 처리할 것 (#304 리뷰):**
> 1. **`ai.call.log.latency` 타이머는 `ai.call.duration`과 완전 중복이다.** `CacheMetricsAdvisor`가
>    잰 `latencyMs`를 그대로 `AiCallLog`에 실어 넘기므로 **같은 숫자를 두 이름으로** 기록한다
>    (서로 다른 구간 측정이 아님). 합산 오류는 없지만 대시보드에 동일 신호가 두 번 뜨고 알람이
>    이중 트리거될 수 있다.
>    → **결정: `ai.call.duration`을 authoritative로 두고 어댑터의 `ai.call.log.latency` 타이머는 제거한다.**
>    카운터 `ai.call.log.recorded`는 유지 — advisor에 없는 "호출 건수·성공여부" 축을 새로 채우므로 중복 아님.
> 2. **`success=false` 태그는 현재 도달 불가.** `CacheMetricsAdvisor`는 정상 응답일 때만 `record()`를
>    부르고 `success = true`로 하드코딩한다(실패 경로는 조기 return).
>    → **결정: Task 1.1에서 고치지 않는다.** 고치려면 `CacheMetricsAdvisor`(client-ai)를 손대야 하는데,
>    그러면 **inprocess 경로도 같이 바뀌어** 실패 건이 core DB(`ai_call_log`)에 새로 쌓인다 = 롤백 경로의
>    동작 변경. Phase 1의 "동작 불변" 원칙에 반한다. **AI 실패율 관측은 Task 1.4에서 HTTP 어댑터 레벨의
>    에러 매핑으로 확보**하고(그쪽이 core가 실제로 겪는 실패다), advisor 개선은 Phase 3 정리 대상.

### Task 1.4: core HTTP 어댑터 배선 + 전환 + 트랜잭션 경계 (불일치 #4)

**Files:**
- Edit: `be/core/core-api/.../adapter/ai/http/*` — Task 0.4 뼈대를 실제 `RestClient` 호출로 구현
- Edit: `core-api` — `AiCheckService` 등 트랜잭션 경계 점검 (⚠️ **`client-ai` 의존은 유지**, 아래 참조)
- Create: HTTP 어댑터 유닛 테스트(MockWebServer 또는 `RestClient` 스텁)

> **⚠️ Task 1.1(#305)에서 이월된 함정 — `Accept` 헤더 406 (착수 시 반드시 반영):**
> ai-api 엔드포인트 24개 중 **2개만 `Content-Type`이 다르다.**
> `/internal/ai/tech-interview/daily-question`·`/explain-followup`은 반환 타입이 순수 `String`이라
> **`text/plain;charset=UTF-8`**로 확정됐다(나머지 22개는 `application/json`).
> 실측 근거: `produces=APPLICATION_JSON_VALUE`를 붙여도 `StringHttpMessageConverter`가 Jackson보다
> 먼저 선택돼 **따옴표 없는 raw text**가 나갔다 → 헤더를 실물에 맞춰 정정한 것.
> → **공용 `RestClient`에 `.accept(MediaType.APPLICATION_JSON)`을 균일하게 걸면 이 2개에서 406
> Not Acceptable이 난다.** 어댑터를 짤 때 ① Accept를 강제하지 않거나 ② 이 2개는 `TEXT_PLAIN`을
> 함께 accept하도록 분기할 것. 응답은 `String`으로 그대로 읽으면 되고 JSON 역직렬화가 필요 없다.
> 계약 회귀 테스트: `ai-api`의 `TechInterviewWireFormatContractTest`가 실제 바이트로 고정하고 있다.
>
> **⚠️ 에러 전파 (같이 결정할 것):** ai-api는 `ApiControllerAdvice`(core-api 소유)를 쓸 수 없어
> **Boot 기본 에러 응답**을 낸다 — 포트 예외 시 500 `{timestamp,status,error,path}`, 필수 필드 누락 시 400.
> `server.error.include-message`가 기본값(`never`)이라 **`message` 필드가 없다** = 실패 원인 문자열이
> core로 전달되지 않는다. → HTTP 어댑터가 원인을 `AiEvaluationException`으로 되살리려면
> ai-api에 `server.error.include-message: always`를 켜거나 전용 에러 바디를 정의해야 한다. **Task 1.4에서 결정.**

**⚠️ 롤백 불변식 (Global Constraints 재확인):** 이 태스크에서 **`client-ai` 컴파일 의존을 제거하지 않는다.**
inprocess 경로가 client-ai 빈을 직접 쓰므로, 떼면 `transport=inprocess` 롤백이 죽어 prod(Fly) 문제 시
되돌릴 안전지대가 사라진다. core-api는 Phase 1 내내 **client-ai(inprocess) + HTTP 어댑터(http) 둘 다
classpath에 두고 피처플래그로 선택**. `client-ai` 제거는 http 프로덕션 기본값 확정·검증 후 **Phase 3 무렵**.

**개념 노트 (트랜잭션):** 진단상 `AiCheckService`가 `@Transactional` 안에서 AI+DB 포트 혼용. HTTP화하면
AI 호출의 네트워크 지연(수 초)·부분 실패가 **DB 커넥션을 잡은 채** 발생 → 커넥션 풀 고갈·장시간 락 위험.
→ AI 호출을 **트랜잭션 밖으로** 재배치(호출 결과를 받은 뒤 짧은 트랜잭션으로 DB 반영) 또는 타임아웃·
폴백 명시. 이건 동작에 영향을 줄 수 있는 리팩토링이라 **태스크 내에서 테스트로 회귀 가드**.

- [ ] **Step 1:** HTTP 어댑터 유닛 테스트(요청 매핑·응답 역직렬화·타임아웃·에러 매핑)
- [ ] **Step 2:** 어댑터 구현. **client-ai 의존 유지**(런타임 선택은 피처플래그) — 의존 제거 금지
- [ ] **Step 3:** `@Transactional` 안 AI 호출을 트랜잭션 밖으로 재배치, 회귀 테스트로 결과 동등성 가드
- [ ] **Step 4:** HTTP 실패 시 폴백/에러 정책(타임아웃, 재시도는 ai-api 소유 or 어댑터) 명시
- [ ] **검증:** `transport=http`·`=inprocess` 양쪽 그린 + Fly 배포 무영향(core-api bootJar 단독 빌드 성공)
- [ ] **검증:** `devquest.ai.transport=http`로 통합 테스트 그린, `=inprocess` 롤백도 그린

### Task 1.5: parity 검증 (완료 판정)

**Files:**
- Create: `be/.../parity/AiParityTest.kt` — in-process vs http 동일 입력 → 응답 스키마·필드 동등 비교

**개념 노트:** parity = "추출이 동작을 안 바꿨다"의 증거. LLM 출력은 비결정적이므로 **텍스트 일치가 아니라
스키마·필드 존재·타입·제약(점수 범위 등) 동등**으로 검증. 결정적 부분(요청 매핑·에러 코드·기본값 복원)은
정확 일치.

- [ ] **Step 1:** 대표 포트(Blog·Resume·TechInterview·CompanyFit) parity 테스트
- [ ] **Step 2:** `transport=http`로 로컬 e2e 스모크(core→ai-api 실제 왕복, ai-api 로컬 기동)
- [ ] **검증:** parity 그린 + 스모크 통과 → **Phase 1 완료**

**Phase 1 완료 판정:** ai-api 독립 기동 · core가 `transport=http`로 ai-api 호출 · 대표 포트 parity 그린 ·
`inprocess` 즉시 롤백 가능 · `./gradlew build` 그린. **아직 EKS 배포 아님**(Phase 3). 프로덕션 기본값은
검증 누적까지 `inprocess` 유지.

---

## 검증 게이트 (verification-before-completion)

- 각 태스크: 해당 모듈 테스트 그린 없이 다음 태스크 금지.
- Phase 경계: `./gradlew build` 전체 그린 + (Phase 1) parity 스모크 증거 첨부.
- "아마 될 것" 금지 — 명령 출력으로 증거 남길 것.

## 미해결 / 결정 로그 (구현 중 채움)

- [ ] **Task 0.1 결과:** Judge0 ai-api 포함 여부 최종 → (추천: 포함)
- [x] **Task 0.2 방침:** **A(관측 재배치) 확정**(2026-07-21 사용자 결정). 소비처 있으면 A+C 병행 → 조사로 최종형 결정
- [x] **Task 0.2 결과 (2026-07-21):** **A 단독 확정** — AiCallLog 읽기 소비처 **0건**(포트=record() write-only,
  repo 커스텀 read 0, 주입처=CacheMetricsAdvisor 하나뿐, 통계/비용 API·조인 없음). 물리적 테이블·어댑터
  삭제는 Phase 3(inprocess 롤백 유지 동안 보존). 상세는 Task 0.2 확정 박스.
- [x] **Task 0.3 결과 (2026-07-21, #298):** 모듈 `core:ai-api` 확정, **`core-domain`만 의존**(client-ai
  연기). client-ai→CacheMetricsAdvisor→AiCallLogPort→db-core 런타임 체인 때문 → **Phase 1은 1.3→1.1 순서**로
  당김(client-ai 붙이기 전 ai-api용 AiCallLogPort 관측 어댑터 필요). Task 1.1 착수 시 scanBasePackages 확대.
- [x] **Task 1.2 결과 (2026-07-22):** client-ai 소비 키(`max-retry`·`judge0.api-key`·`judge0.api-host`)를
  ai-api에 명시 이관(값 동일), `boss-model`/`boss-max-tokens`는 `client-ai-anthropic.yml` 공유로 이관
  불필요(회귀 테스트로 고정). **core-api 키는 삭제하지 않음**(계획 원문 대비 Deviation) — inprocess
  롤백이 살아있는 한 core-api 프로세스가 client-ai 빈을 그대로 호스팅해 여전히 필요. Phase 3(client-ai
  컴파일 의존 제거)에 삭제 예정으로 주석만 남김. `pass-score`·`interview-questions`는 전 코드베이스에서
  소비처 0건 확인 — 이 태스크 범위 밖(손대지 않음). 상세는 Task 1.2 결정 박스.
- [ ] **Task 1.4 결과:** 트랜잭션 경계 재배치 범위(어느 서비스까지)
- 설계 문서의 남은 열린 질문(캐싱·이메일 소유·분산 트레이싱)은 **Phase 2~3** 소관 — 이 계획 범위 밖.

## 다음(이 계획 이후)

- **Phase 2:** daily-service 추출 + 경량 무로그인 FE (설계 §이관계획).
- **Phase 3:** EKS 배포 토폴로지 — Deployment×3·Ingress·NetworkPolicy(2-cluster vpc-cni
  `enableNetworkPolicy` 선행)·노드 용량 t4g.medium 검토. 설계 "EKS 인프라 영향" 체크리스트 사용.
  - **여기서 비로소 `client-ai` 컴파일 의존 제거** — http를 프로덕션 기본값으로 확정하고 검증 누적한
    뒤, inprocess 롤백을 은퇴시킬 때. Phase 1/2 동안은 절대 제거 금지(롤백 불변식).
