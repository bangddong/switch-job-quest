# 서비스 분해 Phase 2 구현 계획 — daily 로직 자립 + 라이브러리 추출

- **상위 설계**: `docs/superpowers/specs/2026-07-20-service-decomposition-design.md`
- **선행 계획**: `docs/superpowers/plans/2026-07-21-service-decomposition-phase01.md` (Phase 0~1, 완료)
- **작성**: 2026-08-03

> ✅ **결정 2건 확정됨 (2026-08-03).** G-1 = *Fly는 단일 유지, 분리는 EKS에서만* / G-2 = *생성과 발송 분리*.
> 이 두 결정이 Stage B의 형태를 바꿨다 — **앱 모듈이 아니라 라이브러리 모듈로 뺀다.** 아래 §확정된 결정 참조.

---

## ⚠️ 이 계획의 근거 — Blindspot Pass 진단 (2026-08-03)

설계의 *"daily는 무인증 · 자체 스키마 · 경량 FE를 함께 출시"* 가정을 실제 코드와 대조한 결과
**불일치**. Phase 0~1 때(부분 일치, 불일치 4건)와 달리 이번엔 **전제 자체가 어긋난다.**

### 확도 🔴 — 직접 확인함

| # | 불일치 | 근거 | 대응 |
|---|--------|------|------|
| **1** | **"무인증 daily"의 읽기 경로가 인증·메일에 매달려 있다.** `getTodayQuestion()`이 `daily_mail_log`를 읽는데, 그 유일한 writer가 `DailyMailScheduler`이고 **① 로그인 유저가 있어야**(`userEmailPort.findAll()` 비면 return) **② 메일이 실제로 나가야만**(`sendDailyTechInterview()==true`) save한다 | `DailyQuestionService.kt:20-26` · `DailyMailScheduler.kt:36-41,66-69` | **Task 2.1** |
| **2** | **`MAIL_ENABLED`가 사실상 daily 제품의 마스터 스위치.** `false`면 `sendDailyTechInterview`가 즉시 `false`를 반환해 save가 스킵된다. 기본값이 `false` | `MailService.kt:19-21` · `application.yml:78` | **Task 2.1** |
| **3** | **경량 무로그인 FE는 이미 있다.** 설계는 "Phase 2에 함께 출시"라고 썼지만 신규 제작이 아니라 **분리**다 | `fe/src/app/App.tsx:195` (`isLoggedIn` 게이트 **앞**) · `DailyQuestionPage.tsx` | **Task 2.8** |
| **4** | **Fly는 앱 1개 하드코딩.** `flyctl deploy`에 `-c`·`--app`이 없어 항상 `be/fly.toml`(=`devquest-api`)을 집는다. **ai-api의 Fly 배포 경로는 0건** — ECR/EKS 쪽에만 뚫려 있다 | `be/fly.toml:1` · `be-cd.yml`(deploy 스텝) · `ecr-push.yml`(`options: [core-api, ai-api]`) | **G-1 / Stage C** |

> 📌 **prod는 지금 200을 준다** (`/api/v1/daily-question` 실측). 로그인 유저가 있고 오늘 메일이
> 나갔기 때문이지 구조가 성립해서가 아니다. **장애가 아니라 구조 결함이고, 안 보이는 게 위험하다.**

### 확도 🟡 — Blindspot Pass가 파일:라인 근거와 함께 보고, 전수 재확인은 안 함

| # | 불일치 | 근거 | 대응 |
|---|--------|------|------|
| 5 | **배포 타겟이 두 문서에서 반대다.** 설계 §확정된 결정 *"배포 전략(확정, 재론 안 함)"* vs CONTEXT *"배포 타겟 열린 결정 — 미정"*. 격리수단(NetworkPolicy vs Fly) 충돌의 **상위 원인** | spec §확정된 결정 · `CONTEXT.md:183` | **G-1** |
| 6 | **Flyway가 모듈로 갈려 있다.** `daily_mail_log`=core-api 리소스 / `tech_question_bank`=db-core 리소스. `flyway.locations` 오버라이드 없음 → daily가 db-core만 의존하면 V6이 안 보인다 | `core-api/.../V6__daily_mail_log.sql` · `db-core/.../V10,V11` | **Task 2.3** |
| 7 | **세 번째 `@SpringBootApplication`도 `scanBasePackages=["com.devquest"]`** → Phase 0에서 43개 테스트를 깨뜨린 빈 누수가 **3자 관계로 재발**한다. 당시 해법은 별도 소스셋 격리였다 | `core-api/build.gradle.kts:10-40`(실측 주석) · `AiApiApplication.kt:15` | **Task 2.4** |
| 8 | **`TechInterviewPort` 하나를 daily·core가 나눠 쓴다.** `evaluate`·`explainFollowup`=daily / `generateDailyQuestion`=스케줄러 / `generateQuestions`=core. **포트가 AI 방향으로만 경계를 긋는다** | `TechInterviewPort.kt:6-16` | **Task 2.6** |
| 9 | **rate-limit 버킷을 daily가 tech-interview와 공유.** 분리하면 `capacity: 2` 예산이 두 JVM으로 쪼개져 **총 예산이 조용히 2배** | `WebMvcConfig.kt:17` · `application.yml:84` | **Task 2.2** |
| 10 | **HTTP 어댑터 인프라가 core-api 안에 있다.** daily가 ai-api를 호출하려면 복제하거나 공유 모듈로 승격해야 한다 | `core-api/.../adapter/ai/http/*` · `AiHttpClientConfig` | **Task 2.5** |
| 11 | **`AiTransportConfig`가 core-api 전용** → daily는 `inprocess` 폴백이 원천 불가, **태어날 때부터 HTTP-only**. plan의 "롤백 불변식"이 적용되지 않는 서비스가 하나 생긴다 | `AiTransportConfig.kt`(패키지 `core.api.config`) | **§Global Constraints** |
| 12 | **`core-api`는 `jar`가 꺼져 있어 다른 모듈이 의존 못 한다.** 옮길 대상이 전부 core-api에 있으므로 **Phase 1의 "무행동 이동 PR" 패턴이 여기선 성립하지 않는다** | `core-api/build.gradle.kts:5-7` · `ai-api/build.gradle.kts:3-11`(실측) | **Task 2.6** |
| 13 | **`ArchAiPortConventionTest`가 포트 목록을 하드코딩** — 이 에픽의 유일한 구조 가드. daily 소유로 옮기면 깨진다 | `ArchAiPortConventionTest.kt:23-42,62-76` | **Task 2.6** |
| 14 | **평가자 개수가 문서마다 17·18·24로 다르다** | spec §결정적 발견 · `ArchAiPortConventionTest.kt:47` · `CONTEXT.md:145` | **Task 2.0** |
| 15 | **`fe/vercel.json`이 `/api/(.*)` 전부를 core 호스트로 rewrite** + `fe-cd.yml`이 Vercel 프로젝트 1개 하드코딩. 설계의 `/daily` path 라우팅과 FE 현재 경로(`/api/v1/daily-question`)가 안 맞는다 | `fe/vercel.json:4-5` · `fe-cd.yml` | **Task 2.8** |
| 16 | **`ai-api`에 Spring Security가 클래스패스에 없다** + `scanBasePackages=["com.devquest"]`라 core-api security가 함께 스캔될 수 있다 | `ai-api/build.gradle.kts` | **G-1 종속** |
| 17 | **`fly.toml`은 scale-to-zero가 아니다** (`min_machines_running=1`). 복사해 daily 앱을 만들면 설계의 비용 전제가 깨진다 | `be/fly.toml:26-28` | **Stage C** |
| 18 | **phase01 plan의 결정 로그에 이미 끝난 항목이 미체크로 남아 있다** (Task 0.1 Judge0 포함 여부, Task 1.4 트랜잭션 범위). Phase 2가 인계 목록으로 읽으면 **끝난 걸 다시 결정한다** | `phase01.md` §미해결/결정 로그 | **Task 2.0** |
| 19 | **CodingQuest가 core와 daily에 동시 배정** — 그리고 가장 얽힌 코드(`generateProblem`/`submitCode`, 트랜잭션 재배치 보류)가 정확히 그 자리에 있다 | spec §서비스 경계 상세 · `CONTEXT.md:250-252` | **G-2 종속** |
| 20 | **설계가 "Phase 1에서 실증"이라 못박은 AiCheck 경계가 실증 없이 Phase 1 완료 선언됐다.** 완료 판정 기준이 설계와 plan에서 다르다 | spec §열린 질문 · `CONTEXT.md:266` | **Task 2.0** |

---

## ✅ 확정된 결정 (2026-08-03)

### G-1 → **Fly는 단일 유지, 분리는 EKS에서만**

상시 prod는 `core-api` 하나 그대로다. 3서비스 토폴로지는 **EKS 실습에서만** 검증한다.

**왜**: 설계가 이미 *"EKS=실습 / Fly=상시"* 라고 못박았는데 §확정된 결정의 *"NetworkPolicy만"* 은
EKS 전제였다. 둘을 동시에 만족시키는 유일한 배치가 이것이다. 부수 효과로 **설계의 격리 전제가
수정 없이 살아남고**, `be-cd.yml`·`fly.toml`·비용이 전부 불변이다(#4·#17 무력화).

**받아들이는 대가 (명시)**:
- 분리된 구조가 **상시 환경에서는 검증되지 않는다.** EKS 세션에서만 돈다.
- `/internal/ai/**` 무인증 문제(#16)는 **EKS 안에서만 노출**되므로 NetworkPolicy로 충분하다
  → 설계 원안대로 **Phase 3 소관**. `.claude/CONTEXT.md`의 *"인증·격리를 Phase 2 첫 태스크로"* 는
  Fly 배포를 가정한 서술이었고, **이 결정으로 근거가 사라졌다** → CONTEXT 정정 대상(Task 2.0).

**🔑 이 결정이 Stage B의 형태를 바꾼다.** Fly가 계속 core-api를 쓰므로 *"core에서 daily 코드를 제거"*
하는 단계가 **영영 오지 않는다.** 앱 모듈로 빼면 core용·daily용 **구현이 두 벌**이 되어 드리프트가
확정적이다. → **라이브러리 모듈로 뺀다**(§Stage B). 한 벌의 구현을 두 조립이 공유한다.

### G-2 → **생성과 발송을 분리**

크론(`DailyMailScheduler`, `@Scheduled`)은 1개로 유지하고 **메서드 안에서 게이트를 둘로 나눈다**:
①콘텐츠 생성(`DailyQuestionContentService.ensureTodayQuestion()` 위임, 유저 수·`MAIL_ENABLED` 무관)
②메일 발송(생성분을 읽어 보냄). 실제 구현(`1bf075a`)은 크론 자체를 쪼개지 않았다 —
독립된 두 크론으로 나누면 스케줄 시각이 어긋날 때 발송 크론이 생성 크론보다 먼저 돌 수 있어
실행 순서 경합이 생긴다. 단일 크론 안에서 ①→② 순서를 코드로 강제하는 편이 더 단순하고
경합이 원천적으로 없다.

**왜**: daily가 자립해야 무인증 서비스가 성립한다(#1·#2). (b)요청시 생성은 첫 요청자가 AI 지연을
전부 먹고 동시 요청 시 중복 과금이며, (c)시드 결정론은 *"매일 새로 생성되는 질문"* 이라는 제품
성격을 바꾼다. (a)만 현재 제품을 유지하면서 결함을 없앤다.

---

## 참고 — 기각한 선택지 (재론 방지)

### G-1. 배포 타겟

문서 2개가 반대 상태였다(#5). 아래 3안을 놓고 비교한 끝에 **넷째 안(Fly 단일 + EKS 실습)**을 택했다.
셋 다 기각 사유가 분명하므로 재론하지 않는다.

| 선택지 | 격리 수단 | CI 변경 | 비용 | 걸리는 것 |
|---|---|---|---|---|
| **(a) Fly 3앱** | Fly 사설망(`.internal`) + 내부 포트 바인딩 | `be-cd.yml`을 앱별 매트릭스로, `fly.*.toml` 3개 | 앱당 머신 — `min_machines_running=1`이면 3배(#17) | 설계의 NetworkPolicy 전제가 통째로 무효 |
| **(b) EKS 완전체** | ClusterIP + NetworkPolicy (설계 원안) | ECR Push는 이미 다중 서비스 지원 | destroy-after-use라 **상시 서비스 불가** | 크레딧 만료 2027-01-15 이후 갈 곳이 없다 |
| **(c) 혼합** — Fly 상시 / EKS 학습 | 환경별로 **다른 수단** | 양쪽 다 | Fly 비용 + 실습 비용 | 격리 구현이 2벌, 드리프트 1순위 |

> 🔑 **셋 다 "상시 환경에도 분리를 올린다"를 전제한다는 게 공통 함정이었다.** 그 순간
> 설계의 NetworkPolicy 전제가 깨지거나(a·c) prod를 포기하게 된다(b).
> 확정안은 그 전제 자체를 버린다 — **상시는 안 나눈다.** 그러면 세 문제가 동시에 사라진다.

### G-2. daily 콘텐츠의 소유

"오늘의 질문"을 **누가 만드나**(#1·#2). (a)로 확정. (b)·(c)의 기각 사유는 아래 표.

| 선택지 | 내용 | 트레이드오프 |
|---|---|---|
| **(a) 생성과 발송을 분리** | 스케줄러를 둘로: ①콘텐츠 생성(무조건) ②메일 발송(생성분을 읽어 보냄) | daily가 자립한다. 새 테이블 1개. **← 채택** |
| **(b) daily가 요청 시 생성** | `getTodayQuestion()`이 없으면 그 자리에서 AI 호출 | 첫 요청자가 AI 지연을 다 먹고, 동시 요청 시 중복 생성 |
| **(c) 시드에서 결정론적 선택** | `tech_question_bank`에서 날짜 해시로 고름 | AI 호출 0, 가장 단순. 대신 "매일 새 문제" 성격이 바뀐다 |

---

## Global Constraints

Phase 0~1의 제약을 **그대로 승계**하고 아래를 추가한다.

- **TDD** / **헥사고날 거부 규칙**(core-domain에 Spring 금지 · db-core↔client-ai 금지 · `!!` 금지) 유지.
- **`client-ai` 의존 제거 금지** — 롤백 불변식. Phase 3까지 유효.
- 🔑 **daily 로직은 라이브러리 모듈에 산다. 앱 모듈로 복제하지 않는다 (G-1 귀결).**
  `core-api`(Fly 상시)와 `daily-api`(EKS 실습) **둘 다** 그 라이브러리를 의존한다.
  구현 한 벌 · 조립 두 개. 복제하면 상시 환경에서 검증되는 쪽만 살아 있고 다른 쪽이 조용히 썩는다.
- 🔑 **그래서 "무행동 이동 PR" 패턴이 되살아난다 (#12 해소).** `core-api`의 `jar`가 꺼져 있는 건
  **다른 모듈이 core-api를 의존할 때** 문제다. 반대 방향(core-api → 라이브러리)은 제약이 없다.
  daily 코드를 라이브러리로 옮겨도 core-api가 그걸 의존하면 **빈 구성이 그대로 유지**된다
  → 이동 PR을 **동작 무변경**으로 만들 수 있다. (검증: 기존 테스트 전량 그린 = 완료 판정)
- 🔴 **롤백 불변식이 daily-api에는 적용되지 않는다 (#11).** daily-api는 `client-ai`가 없어
  `inprocess` 폴백이 원천 불가하고 **태어날 때부터 HTTP-only**다. 다만 G-1 덕분에 **위험이 작다** —
  daily-api는 상시 트래픽을 받지 않으므로, 실패해도 EKS 실습 안에서 끝난다.
  **상시 경로의 롤백은 "core-api를 그대로 두는 것"** 이고, 이건 아무것도 안 하면 유지된다.
- **Fly 배포 무영향 (강한 불변식)**: 이 계획의 **어떤 태스크도** `be/fly.toml`·`be-cd.yml`을
  건드리지 않는다. prod 배포 산출물은 Phase 2 내내 `core-api` 단일 jar다.
  ⚠️ Stage A는 core-api의 **동작을 바꾸므로**(G-2) 이 불변식의 예외다 — 배포 *구성*이 아니라
  *동작* 변경이고, 회귀 테스트로 지킨다.
- **시크릿 안전**: `application-local.yml` 복제·이동·출력 금지. 새 모듈 설정은 `${ENV:}` 플레이스홀더만.

---

## Stage A — daily 도메인을 자립시킨다 (게이트 무관, core 안에서)

> 이 Stage는 **에픽이 멈춰도 단독으로 가치가 있다.** 지금 `/daily-question`은
> "로그인 유저 존재 + 메일 발송 성공"에 매달려 있고, 그 사실이 어디에도 안 적혀 있다.

### ✅ Task 2.0: 문서 정합화 (선행, 코드 0) — **완료 2026-08-03**

처리 결과:
- **#14 숫자** → 셋 다 맞았다. **서로 다른 것을 세고 있었다**: 17=마커 단 LLM 포트 ·
  18=+Judge0Port(=ai-api 컨트롤러 수) · 24=엔드포인트. 설계문서에 **§숫자 규약** 신설,
  CONTEXT의 *"AI 포트 24개"*(실제로는 엔드포인트) 정정.
- **#18 끝난 항목 미체크** → `phase01.md` 최상단에 **실행 완료 배너** + 결정 로그 2건을
  실측 근거와 함께 채움(Judge0=포함하되 마커 제외 / 트랜잭션 재배치=#308 완료, 1건 의도적 보류).
- **#20 AiCheck 경계** → 설계가 *"Phase 1에서 실증"* 이라 했으나 실증 없이 완료 선언된 것을 확인.
  **기각하지 않고 Stage B로 이월** — 라이브러리 분리 시 "오케스트레이션이 어디 남는지"가
  그 자리에서 강제로 드러나므로, 별도 실증보다 그때 결정하는 편이 싸다.
- **G-1 귀결 정정 2건** → CONTEXT의 *"배포 타겟 미정"*·*"인증·격리를 Phase 2 첫 태스크로"* (#357에서 처리).

<details><summary>원래 계획 내용</summary>
- 평가자 개수를 **하나의 숫자로 확정**(#14) — 코드(`ArchAiPortConventionTest`)를 단일 출처로.
- `phase01.md` 결정 로그의 **이미 끝난 항목 체크**(#18) — Phase 2가 다시 결정하지 않도록.
- AiCheck 경계 실증 항목(#20)을 **Phase 2 소관으로 이월하거나 명시적으로 기각**.
- 🔴 **G-1 확정에 따른 정정 2건**:
  - `.claude/CONTEXT.md`의 *"인증·격리를 Phase 2 첫 태스크로"* → **근거 소멸**. Fly 배포를 가정한
    서술이었는데 daily·ai는 Fly에 안 올라간다. 설계 원안대로 **Phase 3(EKS) 소관**으로 되돌린다.
  - `.claude/CONTEXT.md`의 *"배포 타겟 열린 결정 — 미정"* → **확정**으로 갱신.
    설계 §확정된 결정과 어긋나 있던 상태를 해소한다(`design-change-procedure` 4단계).
- 완료 판정: 설계·plan·CONTEXT 세 문서에서 같은 대상의 서술이 어긋나지 않는다.
</details>

### Task 2.1: 콘텐츠 생성을 메일 발송에서 분리 (#1·#2) — **G-2 (a) 구현**
- 새 테이블: 날짜별 오늘의 질문 1건 (**Task 2.3에서 위치 확정 후 작성**).
- `DailyMailScheduler`의 크론은 유지한 채 메서드 내부에서 게이트를 둘로 나눈다: ①생성
  (`DailyQuestionContentService.ensureTodayQuestion()` 위임, 유저 수·`MAIL_ENABLED` 무관)
  ②발송(생성분을 읽어 보냄). 크론 자체를 쪼개지 않은 이유는 실행 순서 경합 — 별도 크론이면
  발송 크론이 생성 크론보다 먼저 도는 경우를 배제할 수 없다(실제 구현: `1bf075a`).
- `getTodayQuestion()`이 `daily_mail_log`가 아니라 새 테이블을 읽는다.
- **동작 변경 있음** → 단독 PR. 회귀 테스트: 유저 0명·`MAIL_ENABLED=false`에서도 오늘의 질문이 나온다.
- ⚠️ 기존 `daily_mail_log`는 **발송 이력**으로 남긴다(중복 발송 방지 용도는 그대로).

### Task 2.2: rate-limit 버킷 분리 (#9)
- `/api/v1/daily-question/evaluate`를 tech-interview 버킷에서 떼어 daily 전용 버킷으로.
- ⚠️ 분리 후 **총 예산이 2배가 되지 않도록** 용량을 재산정한다(현재 `capacity: 2` 공유).
- 인메모리·`Fly-Client-IP` 의존은 이 태스크 범위 밖(Stage C에서 재검토 — EKS로 가면 그 분기는 죽는다).

### Task 2.3: daily 소유 테이블의 마이그레이션 위치 확정 (#6)
- 현재 `daily_mail_log`(core-api 리소스) / `tech_question_bank`(db-core 리소스)로 갈려 있다.
- daily-api가 어느 모듈을 의존할지에 따라 보이는 마이그레이션이 달라진다 → **먼저 정한다.**
- 기존 Neon(12개 적용 완료)의 **히스토리 불일치를 만들지 않는 방법**을 함께 확정.

---

## Stage B — daily 로직을 라이브러리 모듈로 (Stage A 완료 후)

> ⚠️ **태스크 번호가 실행 순서와 다르다** (2.5 → 2.6 → 2.4). 의존 순서를 따른 것 —
> 공유 인프라를 먼저 옮기고, 로직을 라이브러리로 뺀 뒤, 마지막에 그걸 조립하는 앱을 만든다.
> 번호는 Blindspot 진단 항목과의 대응을 유지하려고 그대로 뒀다.

> **목표는 "core에서 떼어내기"가 아니라 "두 조립이 공유할 수 있는 형태로 만들기"다.**
> Fly는 계속 core-api를 쓰므로(G-1) core-api의 동작은 이 Stage 내내 불변이어야 한다.

### Task 2.5: HTTP 어댑터 인프라를 공유 위치로 (#10)
- `BaseAiHttpAdapter`·`AiHttpClientConfig` 등이 지금 `core-api` 안에 있다.
- daily가 ai-api를 호출하려면 공유돼야 한다. **복제 금지** — 두 벌이면 계약이 갈린다
  (Phase 1의 "계약 단일 출처" 원칙 승계).
- 완료 판정: **기존 테스트 전량 그린**(동작 무변경).

### Task 2.6: daily 로직을 라이브러리 모듈로 이동 (#8·#12·#13)
- 새 모듈(가칭 `core:daily-domain`) — **앱이 아니라 라이브러리**. `@SpringBootApplication` 없음.
- 옮기는 것: `DailyQuestionService`, daily rate-limit 인터셉터, Task 2.1이 만든 콘텐츠 생성기.
- `core-api`가 이 모듈을 의존 → **빈 구성 그대로 유지 = 동작 무변경**(#12 해소).
- `TechInterviewPort` 분할 여부 결정(#8) — daily가 쓰는 2개(`evaluate`·`explainFollowup`)를
  가를지, 포트를 공유한 채 둘지. **가르면 `ArchAiPortConventionTest`를 갱신**(#13, 삭제 아님).
- 완료 판정: 기존 테스트 전량 그린 + `./gradlew build` + prod 스모크.

### Task 2.4: `daily-api` 앱 모듈 스캐폴드 (#7) — **EKS 전용**
- 세 번째 `@SpringBootApplication`. `core:daily-domain`을 의존해 조립만 한다.
- 🔴 **Phase 0의 빈 누수를 먼저 막는다.** `AiApiApplication`·`DevQuestApplication` 둘 다
  `scanBasePackages=["com.devquest"]`라(실측 확인) 세 번째가 생기면 3자 관계로 재발한다.
  당시 해법(별도 소스셋 클래스패스 격리)을 확장 적용하거나, **이번엔 스캔 범위를 좁히는 쪽**을 검토.
- ⚠️ 헬스체크: ai-api에는 `/health` 컨트롤러가 없다(core-api에만 있다). daily-api도 같은 함정 —
  k8s 프로브 3개가 전부 `/health`를 본다(`k8s/base/core-api.yaml`).
- 완료 판정: daily-api 단독 기동 + **core-api 배포 산출물 불변**.

---

## Stage C — EKS 토폴로지 (Fly 무작업)

**G-1에 따라 Fly 쪽 작업은 없다.** `fly.toml`·`be-cd.yml` 불변, prod는 core-api 단일 배포 유지.

- `k8s/base/{ai-api,daily-api}.yaml` 신규 — **현재 0건**(k8s에는 core-api·postgres뿐).
- NetworkPolicy — ai는 ClusterIP 내부 전용(설계 원안 그대로 유효).
  ⚠️ **vpc-cni 애드온에 `enableNetworkPolicy`가 필요**한데 현재 맨몸이다 → `2-cluster/addons.tf` 수정.
- ⚠️ **파드 상한**: t4g.small = 11이고 Stage 3a에서 이미 11/11을 찍었다. JVM이 3개가 되면
  **노드 인스턴스 상향(t4g.medium)이 선행**돼야 한다 — 비용 재산정 필요.
- ⚠️ `Fly-Client-IP` 기반 rate-limit 분기(#9 관련)는 EKS에서 죽는다 → 이 Stage에서 재검토.
- 완료 판정: 클러스터에서 3서비스 e2e — 무로그인으로 오늘의 질문 → AI 설명까지.

### Task 2.8: FE — **이번 Phase에서는 분리하지 않는다** (#3·#15)
- `/daily-question`은 **이미 무로그인으로 동작한다**(`App.tsx:195`). 제품 가치는 이미 나와 있다.
- Fly가 단일 유지이므로(G-1) `vercel.json`의 `/api/(.*)` rewrite도 **그대로 맞다.**
- → **분리 불필요.** 설계의 *"경량 FE 함께 출시"* 는 **이미 충족된 상태**로 기록하고 닫는다.
  (EKS 실습에서 3서비스 e2e를 볼 때만 임시로 경로를 바꿔 확인한다.)

---

## 검증 게이트 (verification-before-completion)

| 주장 | 필요한 증거 |
|---|---|
| Task 2.1 완료 | 유저 0명 + `MAIL_ENABLED=false`에서 `/api/v1/daily-question` 200 (테스트로) |
| Stage A 완료 | `./gradlew build` 그린 + prod 스모크(`/api/v1/daily-question` 200) |
| Stage B 완료 | 기존 테스트 전량 그린 + daily-api 단독 기동 + core-api 배포 산출물 불변 |
| Stage C 완료 | 3서비스 e2e — 무로그인으로 오늘의 질문 → AI 설명까지 |

## 미해결 / 결정 로그 (구현 중 채움)

- [x] **G-1 배포 타겟** → **Fly 단일 유지 / 분리는 EKS에서만** (2026-08-03 확정)
- [x] **G-2 daily 콘텐츠 소유** → **생성과 발송 분리** (2026-08-03 확정)
- [ ] **Task 2.3 결과**: daily 테이블의 마이그레이션 모듈 위치 → (미정)
- [ ] **Task 2.6 결과**: `TechInterviewPort` 분할 여부 → (미정)
- [ ] **Task 2.4 결과**: 3자 빈 누수 대응 — 소스셋 격리 vs 스캔 범위 축소 → (미정)
- [ ] **#19 CodingQuest 소유** (core vs daily) → (미정) — G-1로 **긴급도 하락**.
      Fly가 단일이라 소유가 안 갈려도 상시 운영에 영향이 없다. EKS 토폴로지 설계 시 결정.
- [ ] 설계 §열린 질문 중 Phase 2 소관: 데일리 캐싱 전략 · 이메일 소유(core vs daily) → (미정)
      ⚠️ 이메일 소유는 **Task 2.1이 부분적으로 답한다** — 발송은 core에 남고 콘텐츠 생성만 분리된다.

## 다음 (이 계획 이후)

- **Phase 3**: EKS 배포 토폴로지, `client-ai` 컴파일 의존 제거, 분산 트레이싱.
