# 서비스 분해 Phase 2 — **개요층** (Stage 단위)

- **상위 설계**: `docs/superpowers/specs/2026-07-20-service-decomposition-design.md`
- **선행 계획**: `docs/superpowers/plans/2026-07-21-service-decomposition-phase01.md` (Phase 0~1, 완료)
- **작성** 2026-08-03 · **3층 구조로 재편** 2026-08-06

---

## 🔑 이 문서를 읽는 법 — 3층 중 가운데

| 층 | 사는 곳 | 수명 | 규칙 |
|---|---|---|---|
| **설계** | `specs/` | 길다 | 목표·확정 결정·불변식. 바꿀 땐 `design-change-procedure.md` |
| **개요 ← 이 문서** | `plans/` | 중간 | **결과와 완료 판정만.** 미래 태스크에 *지시*를 쓰지 않는다 |
| **태스크 계획** | PR 본문 | 짧다 | **착수 직전** 생성. Blindspot Pass 필수. 별도 문서로 안 남긴다 |

### 왜 이렇게 바꿨나 (2026-08-06)

이 문서는 원래 277줄이었고 Task 2.0~2.8의 **구현 지시**를 담고 있었다. 그게 실제로 깨졌다:

```
2026-08-03  계획서 작성 (#357) — "새 테이블: Task 2.3에서 위치 확정 후 작성"
2026-08-03  Task 2.1 머지 (#359) — V13을 db-core에 생성        ← 같은 날 위반
```

5개 태스크 앞을 내다본 순서 제약이 **작성 당일 깨졌고, 3일간 아무도 몰랐다.** 게으름이 아니라
Task 2.1이 동작하려면 테이블이 지금 필요해서였다 — **실행해보기 전엔 알 수 없는 것**이었다.

> 🔴 **먼 계획은 "검사가 주장보다 헐거운" 것의 한 형태다.** 코드를 열어보지 않은 사람이 쓴 지시가
> *사고가 끝났다는 인상*을 준다. Task 2.3의 계획 3줄을 쓴 시점엔 `FlywayConfig.kt`도,
> `V13`의 `FROM daily_mail_log`도, H2 CI 공백도 아무도 안 봤다. 그런데 문서에 적히는 순간
> 결정된 것처럼 읽힌다. (2026-08-06 실제로 그 3줄을 집어들었다가 전제 3개가 틀렸다)

**그래서 규칙 셋:**
1. **미래 태스크에 지시를 쓰지 않는다.** 필요한 *결정*만 §열린 결정에 남긴다.
2. **완료 판정은 미리 못 박아도 된다** — 결과 기준이라 안 썩는다.
3. **태스크 상세는 코드를 연 사람이 착수 직전에 쓴다.**

---

## 진행 상황

| Stage | 목표 | 상태 |
|---|---|:--|
| **A** | daily 도메인을 core 안에서 자립시킨다 (게이트 무관) | 🚧 2.0·2.1·2.2 완료 / 마이그레이션 소유 미결 |
| **B** | daily 로직을 **라이브러리 모듈**로 (앱 아님 — G-1 귀결) | ⬜ |
| **C** | EKS 토폴로지 3서비스 (Fly 무작업) | ⬜ |

> Stage A는 **에픽이 멈춰도 단독으로 가치가 있다.** 지금 `/daily-question`은 "로그인 유저 존재 +
> 메일 발송 성공"에 매달려 있고 그 사실이 어디에도 안 적혀 있다.

### 완료 판정 (verification-before-completion)

| 주장 | 필요한 증거 |
|---|---|
| Stage A 완료 | `./gradlew build` 그린 + prod 스모크(`/api/v1/daily-question` 200) + **유저 0명·`MAIL_ENABLED=false`에서도 200** |
| Stage B 완료 | 기존 테스트 전량 그린 + daily-api 단독 기동 + **core-api 배포 산출물 불변** |
| Stage C 완료 | 클러스터에서 3서비스 e2e — 무로그인으로 오늘의 질문 → AI 설명까지 |

---

## ✅ 확정된 결정

### G-1 → Fly는 단일 유지, 분리는 EKS에서만 (2026-08-03)

상시 prod는 `core-api` 하나 그대로다. 3서비스 토폴로지는 **EKS 실습에서만** 검증한다.

**왜**: 설계가 *"EKS=실습 / Fly=상시"* 라고 못박았는데 §확정된 결정의 *"NetworkPolicy만"* 은 EKS
전제였다. 둘을 동시에 만족시키는 유일한 배치다. 부수 효과로 `be-cd.yml`·`fly.toml`·비용이 전부 불변.

**대가 (명시)**: 분리된 구조가 **상시 환경에서는 검증되지 않는다.** `/internal/ai/**` 무인증은
EKS 안에서만 노출되므로 NetworkPolicy로 충분 → 설계 원안대로 **Phase 3 소관**.

> 🔑 **이 결정이 Stage B의 형태를 바꾼다.** Fly가 계속 core-api를 쓰므로 *"core에서 daily 코드를
> 제거"* 하는 단계가 **영영 오지 않는다.** 앱 모듈로 빼면 구현이 두 벌이 되어 드리프트가 확정적이다.
> → **라이브러리 모듈로 뺀다.** 한 벌의 구현을 두 조립이 공유한다.

### G-2 → 생성과 발송을 분리 (2026-08-03)

크론은 1개로 유지하고 메서드 안에서 게이트를 둘로 나눈다: ①콘텐츠 생성(유저 수·`MAIL_ENABLED`
무관) ②메일 발송. 크론 자체를 쪼개지 않은 이유는 **실행 순서 경합** — 별도 크론이면 발송이 생성보다
먼저 돌 수 있다. (구현 `1bf075a`)

### 기각한 선택지 (재론 방지)

**G-1**: (a)Fly 3앱 — 설계의 NetworkPolicy 전제가 통째로 무효 / (b)EKS 완전체 — destroy-after-use라
상시 서비스 불가, 크레딧 만료 후 갈 곳 없음 / (c)혼합 — 격리 구현 2벌, 드리프트 1순위.
> 🔑 셋 다 *"상시 환경에도 분리를 올린다"* 를 전제한 게 공통 함정이었다. 확정안은 그 전제를 버린다.

**G-2**: (b)요청 시 생성 — 첫 요청자가 AI 지연을 다 먹고 동시 요청 시 중복 과금 /
(c)시드 결정론 — AI 호출 0으로 가장 단순하나 *"매일 새 문제"* 라는 제품 성격이 바뀐다.

---

## Global Constraints

Phase 0~1의 제약을 **그대로 승계**하고 아래를 추가한다.

- **TDD** / **헥사고날 거부 규칙**(core-domain에 Spring 금지 · db-core↔client-ai 금지 · `!!` 금지) 유지.
- **`client-ai` 의존 제거 금지** — 롤백 불변식. Phase 3까지 유효.
- 🔑 **daily 로직은 라이브러리 모듈에 산다. 앱 모듈로 복제하지 않는다 (G-1 귀결).**
  구현 한 벌 · 조립 두 개. 복제하면 상시에서 검증되는 쪽만 살고 다른 쪽이 조용히 썩는다.
- 🔑 **"무행동 이동 PR" 패턴이 되살아난다.** `core-api`의 `jar`가 꺼진 건 *다른 모듈이 core-api를
  의존할 때* 문제다. 반대 방향(core-api → 라이브러리)은 제약이 없다.
- 🔴 **롤백 불변식이 daily-api에는 적용되지 않는다.** `client-ai`가 없어 `inprocess` 폴백이 원천
  불가하고 태어날 때부터 HTTP-only다. G-1 덕분에 위험은 작다 — 상시 트래픽을 안 받는다.
- **Fly 배포 무영향 (강한 불변식)**: 어떤 태스크도 `be/fly.toml`·`be-cd.yml`을 건드리지 않는다.
  ⚠️ Stage A는 core-api의 **동작**을 바꾸므로(G-2) 예외다 — 배포 *구성*이 아니라 *동작* 변경이고,
  회귀 테스트로 지킨다.
- **시크릿 안전**: `application-local.yml` 복제·이동·출력 금지. 새 모듈 설정은 `${ENV:}` 만.

---

## 🔍 탐색 결과 — **지시가 아니라 발견이다**

> 이 절은 계획이 아니라 **조사 기록**이다. 착수 시 출발점으로 쓰되, *"이렇게 하라"* 로 읽지 말 것.
> 각 항목은 발견 시점의 사실이며, 그 뒤 코드가 바뀌었을 수 있다 — **착수 직전에 재확인한다.**

### Blindspot Pass · 2026-08-03 (확도 🔴 직접 확인)

| # | 발견 | 근거 |
|---|------|------|
| 1 | **"무인증 daily"의 읽기 경로가 인증·메일에 매달려 있다.** `getTodayQuestion()`이 읽는 `daily_mail_log`의 유일한 writer가 `DailyMailScheduler`이고 ①로그인 유저 존재 ②메일 발송 성공이 있어야 save | `DailyQuestionService.kt:20-26` · `DailyMailScheduler.kt:36-41,66-69` |
| 2 | **`MAIL_ENABLED`가 사실상 daily의 마스터 스위치.** 기본값 `false` | `MailService.kt:19-21` |
| 3 | **경량 무로그인 FE는 이미 있다.** 신규 제작이 아니라 분리 | `fe/src/app/App.tsx:195` |
| 4 | **Fly는 앱 1개 하드코딩.** ai-api의 Fly 배포 경로 0건 | `be/fly.toml:1` · `be-cd.yml` |

> 📌 **prod는 지금 200을 준다.** 로그인 유저가 있고 오늘 메일이 나갔기 때문이지 구조가 성립해서가
> 아니다. **장애가 아니라 구조 결함이고, 안 보이는 게 위험하다.**

### Blindspot Pass · 2026-08-03 (확도 🟡 파일:라인 근거, 전수 재확인 안 함)

| # | 발견 | 근거 |
|---|------|------|
| 6 | **Flyway가 모듈로 갈려 있다.** 버전이 **교차**한다 — core-api `V1~V6,V8,V9` / db-core `V7,V10~V13` | 각 모듈 `db/migration/` |
| 7 | **세 번째 `@SpringBootApplication`도 `scanBasePackages=["com.devquest"]`** → Phase 0에서 43개 테스트를 깨뜨린 빈 누수가 3자로 재발 | `AiApiApplication.kt:15` |
| 8 | **`TechInterviewPort` 하나를 daily·core가 나눠 쓴다.** 포트가 AI 방향으로만 경계를 긋는다 | `TechInterviewPort.kt:6-16` |
| 10 | **HTTP 어댑터 인프라가 core-api 안에 있다.** daily가 ai-api를 부르려면 공유 승격 필요 | `core-api/.../adapter/ai/http/*` |
| 11 | **`AiTransportConfig`가 core-api 전용** → daily는 태어날 때부터 HTTP-only | `AiTransportConfig.kt` |
| 12 | **`core-api`는 `jar`가 꺼져 있어 다른 모듈이 의존 못 한다** | `core-api/build.gradle.kts:5-7` |
| 13 | **`ArchAiPortConventionTest`가 포트 목록을 하드코딩** — 이 에픽의 유일한 구조 가드 | `ArchAiPortConventionTest.kt:23-42,62-76` |
| 15 | **`fe/vercel.json`이 `/api/(.*)` 전부를 core 호스트로 rewrite** | `fe/vercel.json:4-5` |
| 16 | **`ai-api`에 Spring Security가 클래스패스에 없다** | `ai-api/build.gradle.kts` |
| 17 | **`fly.toml`은 scale-to-zero가 아니다**(`min_machines_running=1`) | `be/fly.toml:26-28` |
| 19 | **CodingQuest가 core와 daily에 동시 배정** — 가장 얽힌 코드가 정확히 그 자리에 | spec §서비스 경계 |

### Blindspot Pass · 2026-08-06 — 마이그레이션 소유 조사 중 발견

착수하려다 **전제 3개가 틀린 걸 발견해 멈췄다.** 아래는 그때 나온 것들이다.

| # | 발견 | 근거 |
|---|------|------|
| **21** | 🔴 **V13이 V6 테이블에 SQL로 하드 의존.** db-core만 실린 클래스패스로 빈 DB에 돌리면 `relation "daily_mail_log" does not exist`로 즉사 | `V13__create_daily_question_content.sql:40` (`FROM daily_mail_log`) |
| **22** | 🔴 **마이그레이션이 CI에서 한 번도 실행되지 않는다.** 테스트는 H2 + `ddl-auto: create-drop`, Flyway 구동 테스트 0건 → **어떤 결정도 배포 전 반증 불가**. V8 사고가 CI가 아니라 Loki 로그로 발견된 이유 | `db-core.yml:4-13` |
| **23** | 🔴 **위험 위치는 Neon이 아니라 EKS in-cluster Postgres.** G-1로 daily-api는 Fly에 안 간다. Neon은 core-api 단독이라 충돌 불가 | `k8s/eso/externalsecret-db-incluster.yaml:51-66` |
| **24** | 🔴 **파괴 경로는 "DELETED 표시"가 아니라 재적용 시도.** `FlywayConfig`가 `repair()`→`migrate()` 순이라, 클래스패스 결손 → DELETED → **V1부터 재실행 → 영구 부팅 불가** | `FlywayConfig.kt:21-22` (flyway-core 11.14.1) |
| **25** | **파일 이동은 Flyway에 무해하다.** `script`는 location 기준 상대경로, `checksum`은 내용 해시. `locations`가 하나뿐이라 어느 모듈에 있든 동일 | `FlywayConfig.kt:19` |
| **26** | 🔴 **설계가 확정한 "daily=자체 스키마"에 코드가 0줄.** `schemas()`/`defaultSchema()` 호출 0건, 마이그레이션 13개 전부 `public` | spec:60 (`🔴 확정 07-20`) vs `FlywayConfig.kt` |
| **27** | **`spring.flyway.enabled: false`는 죽은 설정.** SB4에서 Flyway 자동설정이 제거돼 프로그래매틱 설정이 저 키를 안 읽는다 | `application-local.yml:12-13` |
| **28** | **CI 린트는 버전 중복만 본다** — 모듈 이동은 통과. 그리고 `be-ci.yml`은 PR에서만 돈다(CD엔 린트 없음) | `.github/workflows/be-ci.yml:54-67` |
| **29** | **Dockerfile이 모듈 경로를 하드코딩** — daily-api는 반드시 `be/core/` 밑이어야 하고 `ecr-push.yml`의 choice 목록도 손대야 한다 | `be/Dockerfile:3,17,22` |
| **30** | **`ai_call_log`(V7) 선례는 답이 아니다.** Phase 1은 테이블을 옮긴 게 아니라 **읽기 소비처가 0건이라 DB 의존을 버렸다.** daily는 실제로 읽고 쓴다 | `ai-api/.../AiCallLogObservabilityAdapter.kt:12-15` |
| **31** | **ai-api는 한 번도 배포된 적이 없다.** `k8s/base/`에 매니페스트 0건 → "분리는 이미 검증됐다"는 빌드 수준이지 런타임 수준이 아니다 | `k8s/base/` |
| **32** | **BE 모듈 규칙표에 daily-api/daily-domain의 자리가 없고 강제 장치도 없다.** ArchUnit 의존성 0건, 규칙은 문서에만 산다 | `be/CLAUDE.md:5-14` |

---

## ❓ 열린 결정 — **착수할 때 정한다**

> 여기 있는 것은 *"무엇을 정해야 하는가"* 이지 *"어떻게 하라"* 가 아니다.

- [x] **① 마이그레이션 실행 주체** → **core-api 하나로 고정** (2026-08-06 · PR #365 `70580ff`).
      `devquest.flyway.migrate-on-startup` 명시적 opt-in, **기본값 off**. daily-api는 아무것도 안 해도
      안전하다 — *"잊으면 터진다"* 가 아니라 *"잊으면 안 돈다"* 로 뒤집었다.
      <!-- verify: be/storage/db-core/src/main/kotlin/com/devquest/storage/db/core/config/FlywayConfig.kt ~ ConditionalOnProperty -->
- [ ] **② daily 테이블의 데이터 소유 (스키마 분리)** — 설계가 🔴확정(07-20)했으나 **코드 0줄**(#26).
      ①과 **직교한다** — ①은 *누가 돌리는가*, ②는 *어디에 사는가*. 기각이 아니라 순서를 매긴 것이라
      `design-change-procedure` 대상이 아니다. **daily-api가 실제로 생기는 Stage C에서 필요해진다.**
      ⚠️ 착수 시 `V13`의 `FROM daily_mail_log`(#21) 교차 의존을 함께 풀어야 한다.
- [x] **선행: 마이그레이션 CI 공백(#22)** → **해소** (2026-08-06 · PR #364 `6a3c163`).
      실제 Postgres 17.10에 Flyway를 돌린다. 이제 마이그레이션 결정을 **배포 전에 반증할 수 있다.**
      <!-- verify: be/core/core-api/src/test/kotlin/com/devquest/migration/FlywayMigrationIntegrationTest.kt ~ PostgreSQLContainer -->
- [ ] **`TechInterviewPort` 분할 여부** (#8·#13) — 가르면 `ArchAiPortConventionTest` 갱신 필요(삭제 아님)
- [ ] **3자 빈 누수 대응** (#7) — 소스셋 격리 vs 스캔 범위 축소
- [ ] **CodingQuest 소유** (core vs daily, #19) — G-1로 **긴급도 하락**. EKS 토폴로지 설계 시 결정
- [ ] 설계 §열린 질문 중 Phase 2 소관: 데일리 캐싱 전략 · 이메일 소유
      ⚠️ 이메일 소유는 Task 2.1이 부분적으로 답했다 — 발송은 core에 남고 콘텐츠 생성만 분리됐다

### 닫힌 것

- [x] **G-1 배포 타겟** → Fly 단일 유지 / 분리는 EKS에서만 (2026-08-03)
- [x] **G-2 daily 콘텐츠 소유** → 생성과 발송 분리 (2026-08-03)
- [x] **FE 분리 여부** → **분리하지 않는다.** `/daily-question`은 이미 무로그인으로 동작하고(#3),
      G-1로 `vercel.json` rewrite도 그대로 맞다(#15). 설계의 *"경량 FE 함께 출시"* 는 **이미 충족**

---

## 완료된 태스크 기록

| Task | 내용 | 완료 |
|---|---|---|
| **2.0** | 문서 정합화 — 평가자 개수 단일 출처화(#14), phase01 결정 로그 채움(#18), AiCheck 경계 재판정(#20) | 2026-08-03 |
| **2.1** | 콘텐츠 생성을 메일 발송에서 분리 (G-2 구현). `ensureTodayQuestion()` + `V13`(레거시 백필) | 2026-08-03 · PR #359 `60fee5d` |
| **2.2** | rate-limit 버킷 분리 (#9). **tech 2 + daily-evaluate 1 = 총 3** (2+2=4는 "예산 2배"라 기각). `AbstractRateLimitInterceptor`/`...BucketStore` 공통 베이스 = **Stage B의 이동 단위** <!-- verify: be/core/core-api/src/main/kotlin/com/devquest/core/api/support/AbstractRateLimitInterceptor.kt --> | 2026-08-06 · PR #361 `904f7d4` |

> ⚠️ **2.2에서 배운 함정**: `RateLimitResetScheduler`에 새 스토어 `clear()`를 빠뜨리면 자정 리셋이
> 안 되어 **하루 1회 쓰고 영구 차단**된다. `RateLimitResetSchedulerTest`가 이 회귀를 고정한다.

---

## 다음 (이 계획 이후)

**Phase 3**: EKS 배포 토폴로지, `client-ai` 컴파일 의존 제거, 분산 트레이싱.
⚠️ Stage C 착수 전 확인할 것 — vpc-cni에 `enableNetworkPolicy` 필요(현재 맨몸) ·
t4g.small 파드 상한 11인데 Stage 3a에서 이미 11/11 → JVM 3개면 **노드 상향 선행**(비용 재산정).
