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
| **A** | daily 도메인을 core 안에서 자립시킨다 (게이트 무관) | ✅ **완료 (2026-08-21)** — prod 실측 확인 <!-- verify: .github/workflows/prod-smoke-daily.yml ~ daily-question --> |
| **B** | daily **로직**을 라이브러리 모듈로 + 그걸 조립하는 얇은 `daily-api` 앱 (G-1 귀결) | ✅ **완료 (2026-08-27)** — B-1 `core:daily-core`(#392) · B-2a `clients:client-ai-http`(#393) · B-2b `core:daily-api`(#395). 완료 기준 3개 전부 충족: 519 tests 0 failures(직전 497 유지) · daily-api 단독 기동(RANDOM_PORT 실서버 + ai-api 없이 200/404) · **core-api 산출물 334 불변**(diff 공집합) <!-- verify: be/core/daily-api/build.gradle.kts --> |
| **C** | EKS 토폴로지 3서비스 (Fly 무작업) | ⬜ |

> Stage A는 **에픽이 멈춰도 단독으로 가치가 있다.** 지금 `/daily-question`은 "로그인 유저 존재 +
> 메일 발송 성공"에 매달려 있고 그 사실이 어디에도 안 적혀 있다.

### 완료 판정 (verification-before-completion)

| 주장 | 필요한 증거 |
|---|---|
| Stage A 완료 | ~~`./gradlew build` 그린 + prod 스모크(`/api/v1/daily-question` 200) + **유저 0명·`MAIL_ENABLED=false`에서도 200**~~ → ✅ **2026-08-21 충족.** ①`build` 497건 그린(#388) ②**prod 스모크 200 — 05:49 KST 실측**(09:00 cron 보다 **3시간 11분 먼저**, 그 시점엔 오늘 행이 존재할 수 없으므로 그 요청 자체가 lazy 생성을 유발했다) ③읽기 경로 3개 파일에 유저·메일 참조 **0건**. ⚠️ ③은 **prod 에서 유저 0명을 관측한 게 아니라** 코드 경로가 그것들과 무관함을 보인 것이다 — 구분해 적는다 |
| Stage B 완료 | 기존 테스트 전량 그린 + daily-api 단독 기동 + **core-api 배포 산출물 불변**<br>🔴 **"산출물 불변"의 정의 (2026-08-22 B-1 에서 확정)**: **바이트 동일이 아니다** — 클래스를 별도 Gradle 모듈로 빼는 순간 `BOOT-INF/classes/` → `BOOT-INF/lib/<모듈>.jar` 로 배치가 바뀌므로 원리적으로 불가능하다. **core-api 가 싣고 나가는 `com.devquest.*` 클래스 집합이 동일한가**로 판정한다(어느 jar 에 들었는지 무관). ⚠️ **기준선은 변경 전에 재둔다** — 바꾼 뒤엔 못 잰다. 측정법은 `bootJar` 를 열어 `BOOT-INF/classes/` + `BOOT-INF/lib/*.jar` 안의 `com/devquest/**/*.class` 를 합집합으로 모아 정렬·해시한다. B-1 실측: **333 → 334**(증가분은 L-27 해소로 신설한 `DailyQuestionGeneratorPort` 하나, 그 외 완전 일치). **의도된 증감은 diff 로 설명 가능해야 하고, 설명되지 않는 증감이 하나라도 있으면 실패다.** QA 가 `MANIFEST` Start-Class · Spring Boot 레이어 키 · 리소스 diff 까지 함께 확인해 "클래스 집합만 보면 놓치는 것"이 없음을 별도로 검증했다 |
| Stage C 완료 | ~~클러스터에서 3서비스 e2e — 무로그인으로 오늘의 질문 → AI 설명까지~~ → 🔄 **정정 (2026-08-28, 사용자 결정 · D-008)**: 클러스터에서 3서비스 e2e — 무로그인으로 오늘의 질문 → **설명 요청이 daily-api → ai-api 를 거쳐 응답으로 돌아오는 것**까지. ⚠️ **AI 응답은 스텁이다** — 실제 Anthropic 호출이 아니다. 검증 대상은 **토폴로지(3서비스 라우팅·서비스 디스커버리·NetworkPolicy)** 이지 AI 품질이 아니다. 근거는 아래 D-008 |

---

## ✅ 확정된 결정

### G-1 → Fly는 단일 유지, 분리는 EKS에서만 (2026-08-03)

> 📌 **D-006** · 상태 `✅유효` · 영향 `docs/superpowers/specs/2026-07-20-service-decomposition-design.md`(§103·104·120), `be/settings.gradle.kts`, `be/core/core-api/build.gradle.kts`, `be/core/ai-api/build.gradle.kts`, Stage B·C

상시 prod는 `core-api` 하나 그대로다. 3서비스 토폴로지는 **EKS 실습에서만** 검증한다.

**왜**: 설계가 *"EKS=실습 / Fly=상시"* 라고 못박았는데 §확정된 결정의 *"NetworkPolicy만"* 은 EKS
전제였다. 둘을 동시에 만족시키는 유일한 배치다. 부수 효과로 `be-cd.yml`·`fly.toml`·비용이 전부 불변.

**대가 (명시)**: 분리된 구조가 **상시 환경에서는 검증되지 않는다.** `/internal/ai/**` 무인증은
EKS 안에서만 노출되므로 NetworkPolicy로 충분 → 설계 원안대로 **Phase 3 소관**.

> 🔑 **이 결정이 Stage B의 형태를 바꾼다.** Fly가 계속 core-api를 쓰므로 *"core에서 daily 코드를
> 제거"* 하는 단계가 **영영 오지 않는다.** 앱 모듈로 빼면 구현이 두 벌이 되어 드리프트가 확정적이다.
> → **라이브러리 모듈로 뺀다.** 한 벌의 구현을 두 조립이 공유한다.

> 🔑 **"두 조립"이 무엇인지 못박는다 (2026-08-19 · 착수 전 모순 발견으로 추가).**
> `daily 로직` = 라이브러리 모듈 / `조립 1` = `core-api`(Fly 상시) / `조립 2` = `daily-api`(EKS 전용).
> **`daily-api` 앱 모듈은 Stage B 에서 만든다** — 완료 기준의 *"단독 기동"* 이 그것을 검증한다.
> Stage C 는 그 산출물을 **EKS 에 배포**하는 단계이지 새로 만드는 단계가 아니다.
>
> ⚠️ **이 문장의 범위는 `be/` 산출물(Gradle 앱 모듈)에 한정된다 — 인프라 산출물이 아니다** (2026-08-28 감사에서 오독이 실제로 발생해 명시). 아래 #31이 적었듯 **매니페스트는 0건**이고, Stage C 는 그것 말고도 **ECR 레포·워크플로 옵션·NetworkPolicy·노드 용량·daily-api actuator 의존(=BE 코드 변경)**을 새로 만들어야 한다. 착수 전 목록은 아래 **§Stage C 착수 블로커** 참조.
>
> 🔴 **이건 추론이 아니라 이미 살아 있는 선례다.** Phase 1 이 정확히 이 형태로 착지했다(실측):
> ```
> be/clients/client-ai/                  ← 공유 모듈 (ai-api 내부가 아니다)
> be/core/core-api/build.gradle.kts:55   implementation(project(":clients:client-ai"))
> be/core/ai-api/build.gradle.kts:20     implementation(project(":clients:client-ai"))
> ```
> <!-- verify: be/core/core-api/build.gradle.kts ~ clients:client-ai -->
> <!-- verify: be/core/ai-api/build.gradle.kts ~ clients:client-ai -->
>
> ⚠️ **설계서 §104 는 이와 다르게 적혀 있다**(*"client-ai 는 ai-api 에만 묶임"*). 설계가 틀린 게 아니라
> **Phase 1 이 롤백 불변식 때문에 다르게 착지했고 설계서에 반영되지 않았다.** 그쪽에 표시해뒀다.
>
> 📌 이전 표기(`라이브러리 모듈로 (앱 아님)`)는 *"로직이 앱 모듈에 살지 않는다"* 는 뜻이었으나
> **완료 기준 `daily-api 단독 기동`·Global Constraints 의 `롤백 불변식이 daily-api 에는 적용되지 않는다`
> 와 정면으로 읽혀** 착수 직전 혼선을 냈다. 그래서 문구를 고쳤다.

### G-3 → 라이브러리는 순수하게, 웹/에러 배관은 각 조립이 소유 (2026-08-22 · 사용자 결정)

> 📌 **D-007** · 상태 `✅유효` · 영향 `be/core/daily-core/build.gradle.kts`, `be/core/core-api/src/main/kotlin/com/devquest/core/support/`, `be/clients/client-ai-http/build.gradle.kts`, `be/CLAUDE.md`, Stage B·C

**`com.devquest.core.support`(`ApiResponse`·`CoreException`·`ErrorType`·`ErrorCode`, 7파일 110줄)를 공유 모듈로 빼지 않는다.**
라이브러리에는 **서비스만** 들어가고, **컨트롤러·DTO·예외 매핑은 각 앱이 소유**한다.

**왜 이게 결정 사항이었나** (B-1 착수 조사에서 드러난 것):
진짜 난이도는 *"daily 로직 옮기기"* 가 아니라 **웹/에러 배관을 어디까지 공유할지**였다.

| 이동 후보 | core-api 결합 |
|---|---|
| `DailyQuestionContentService` | **0건** — 레포 내부 import 가 전부 `core-domain` |
| `DailyQuestionService` | `CoreException` · `ErrorType` |
| `DailyQuestionController` | `ApiResponse` + DTO |
| 레이트리밋 `Abstract*` | `ErrorCode` |

**기각한 대안**: `support:web` 공유 모듈 신설 → **core-api 의 22개 파일이 import 를 바꿔야** 하고
*"core-api 배포 산출물 불변"* 검증이 까다로워진다. Stage B 가 **두 일(배관 공유 + daily 추출)을 동시에** 하게 된다.

🔑 **수법**: 라이브러리 서비스는 **예외 대신 `null`/도메인 결과를 반환**하고 호출자가 매핑한다.
#387 이 이미 쓴 방식이다(`ensureTodayQuestionFromBank()` → `null` → `DailyQuestionService` 가 404 매핑).

⚠️ **감수하는 대가 (명시)**: `daily-api` 는 `ApiResponse` 를 못 쓰므로 **자체 응답 형식**을 갖는다.
즉 **두 앱의 `/api/v1/daily-question` 응답 스키마가 갈라질 수 있다.**
지금은 감수한다(다른 배포·다른 클라이언트). 🔑 **재검토 트리거: 두 응답이 반드시 같아야 하는 상황이 오면**
그때 배관 공유를 다시 본다.

📌 **부수 결정** (같은 자리에서 확정, B-1 에서 구현):
- `DailyMailScheduler` → **core-api 잔류** (`MailService` 는 core 소유 — G-2 귀결. daily-api 엔 메일 없음)
- 레이트리밋 `Abstract*` → **core-api 잔류** (`ErrorCode` 의존 + tech 인터셉터도 쓴다. 옮기면 방향 역전)
- 라이브러리는 **`core-domain` 포트만** 의존, 어댑터 배선은 각 조립 (`client-ai` 패턴)
- 모듈명 **`core:daily-core`** (`db-core` 의 *"-core = 공유 조각"* 관례)
  <!-- verify: be/core/daily-core/build.gradle.kts ~ core:core-domain -->

### G-4 → 학습 클러스터의 AI 호출은 **스텁**으로 대체 (2026-08-28)

> 📌 **D-008** · 상태 `✅유효` · 영향 `docs/superpowers/plans/2026-08-03-service-decomposition-phase02.md`(Stage C 완료 기준), `infra/aws-eks/2-cluster/secrets.tf`, `be/core/ai-api/src/main/resources/application.yml`, `be/clients/client-ai/src/main/resources/client-ai-anthropic.yml`, Stage C

**`ANTHROPIC_API_KEY` 를 학습 클러스터에 넣지 않는다.** ai-api 에 **학습 전용 스텁 프로필**을 두고 고정 응답을 돌려준다.

**왜 이게 결정 사항이었나** (2026-08-28 감사에서 드러남):
Stage C 완료 기준이 *"AI 설명까지"* 를 요구하는데 **그 키가 인프라 어디에도 없었다** — `devquest-eks/app` 시크릿의 키는 `JWT_SECRET`·`GITHUB_CLIENT_ID`·`GITHUB_CLIENT_SECRET` 3개뿐이다. 그런데 키를 넣는 것 자체가 `secrets.tf` 가 세운 **명시적 원칙**(*"학습 클러스터에 prod 크리덴셜을 넣지 않는다"*)과 정면으로 부딪힌다. 같은 파일이 **더미 Grafana 키를 넣었다가 실제 스택에 인증 시도가 새어나간 사고**를 기록해뒀다 — 원칙이 경험에서 나온 것이다.

🔴 **더 나쁜 성질**: `client-ai-anthropic.yml` 이 `${ANTHROPIC_API_KEY:}`(**빈 문자열 기본값**)라 키가 없어도 **부팅은 성공하고 AI 호출 시점에 실패**한다. CrashLoop 가 아니라 런타임 실패라 **과금 구간 안에서** 진단하게 된다 — SOP 가 가장 비싼 실수로 지목한 형태다.

**기각한 대안**:
- **별도 예산 키를 세션 동안만 주입** — 원칙의 취지(prod 자격증명 격리)는 지키지만 **사용자가 키 발급·한도 설정을 직접 해야 하고**, 실습마다 주입/삭제 절차가 늘어 *"끄는 걸 잊는 것"* 의 표면을 넓힌다.
- **prod 키를 그대로 사용** — 가장 빠르나 `secrets.tf:111-118` 의 원칙을 깬다. 원칙을 바꾸려면 그 자체가 `design-change-procedure` 대상이다.

⚠️ **감수하는 대가 (명시)**: **검증 범위가 좁아진다.** Stage C 가 증명하는 것은 **토폴로지**(3서비스가 클러스터에서 서로를 찾고 라우팅되는가)이지 *"AI 설명이 실제로 나온다"* 가 아니다. 완료 기준 문장을 그에 맞게 정정했다(위 표).
🔑 **재검토 트리거**: ①실제 LLM 응답이 필요한 검증(응답 지연·타임아웃 튜닝·토큰 비용 실측)을 하게 되면 그때 **별도 예산 키**를 다시 본다 ②Phase 3 에서 분산 트레이싱을 붙일 때 스텁이 trace 를 왜곡하면 재판정.

📌 **부수 결정** (같은 자리에서 확정):
- **스텁은 ai-api 소유**다 — `client-ai` 를 건드리지 않는다(롤백 불변식: *"`client-ai` 의존 제거 금지"*, 아래 #193).
- 스텁 활성화는 **프로필 또는 프로퍼티 opt-in**, **기본값 off**. 열린 결정 ① 이 세운 *"잊으면 터진다가 아니라 잊으면 안 돈다"* 형태를 따른다 — prod 에 스텁이 조용히 켜지는 일이 없어야 한다.
- 🔴 **스텁 응답은 스텁임이 눈에 보여야 한다.** 고정 문자열에 식별 가능한 표식을 넣는다 — *"통과했다고 믿게 만드는 검사"* 를 만들지 않는다(Stage A 스모크에서 세운 원칙).

### G-5 → 노드는 **t4g.medium 으로 상향** (2026-08-28)

> 📌 **D-009** · 상태 `✅유효` · 영향 `infra/aws-eks/2-cluster/variables.tf`, `infra/aws-eks/2-cluster/addons.tf`, `k8s/base/core-api.yaml`, `docs/eks-migration-log.md`, Stage C

**`node_instance_type` 을 `t4g.small` → `t4g.medium` 으로 올린다.** 노드 대수는 1대 유지.

**왜**: JVM 3개를 올려야 하는데 **파드도 메모리도 모자란다.**
- 파드: 베이스라인 **10/11**(Stage 3b 의 `coredns replicaCount=1` 반영) → 2개 추가 시 **12 > 11**
- 🔴 메모리가 먼저 막는다: Stage 3a 실측 스케줄러 메시지가 `Insufficient memory, Too many pods` **둘 다**였다(`docs/eks-migration-log.md:1079`). requests 합 추정 **1792Mi**(512×3 + 256), limits 합 **3212Mi** — t4g.small 물리 2GiB 로는 requests 조차 안 들어간다

**기각한 대안 — 노드 2대**: 파드 슬롯만 푼다. **파드 하나당 512Mi 요구라는 메모리 벽은 그대로**다. 게다가 `nodes.tf` 가 `subnet_ids` 를 `persistent_az` **단일 AZ 로 핀**해 2대가 같은 AZ 에 뜨므로 가용성 이득도 없다. DaemonSet 3개가 새 노드 자리를 먼저 먹어 순증은 11이 아니라 **8**(coredns 를 2로 되돌리는 자기 규율까지 따르면 **7**). 비용도 **+$0.026/h 로 medium(+$0.021 추정)보다 비싸다.**

⚠️ **미확인 — 착수 시 반드시 실측한다** (레포에 기록 0건):
| 값 | 현재 상태 |
|---|---|
| ~~t4g.medium 온디맨드 단가~~ | ✅ **실측 완료 (08-31)**: `$0.0416/h` — small 의 **정확히 2배**. 세션 총액 **$0.149/h**. 일지의 `$0.16` 은 **$0.011 과다**였다 |
| ~~t4g.medium 파드 상한 (*"17"*)~~ | ✅ **실측 완료 (08-31)**: ENI 3 × IPv4 6 → `3×(6-1)+2` = **17**. 🔑 같은 조회로 small = **11** 이 재현돼 **공식 자체가 검증된 상태**에서 얻은 값이다 (메모리도 확인: 2048 → **4096 MiB**) |
| 🔴 노드 `Allocatable.memory` | **여전히 실측 0건.** 물리 4096MiB 에서 kube-reserved·eviction 을 뺀 실제 가용량을 모른다 → requests 합 1792Mi 가 들어가는지는 **추정**이다. **apply 세션에서 `kubectl describe node` 로 재고 이 표·`variables.tf` 주석·일지를 함께 갱신할 것** |

🔑 **`addons.tf` 의 자기 규율은 여전히 유효하다** — *"노드를 2대 이상으로 늘리면 coredns 를 2로 되돌릴 것"*. 이 결정은 **1대를 유지**하므로 `replicaCount=1` 을 그대로 둔다.

### G-2 → 생성과 발송을 분리 (2026-08-03)

> 📌 **D-005** · 상태 `🔄부분무효` · 영향 `be/core/daily-core/src/main/kotlin/com/devquest/core/domain/DailyQuestionContentService.kt`, `be/core/core-api/src/main/kotlin/com/devquest/core/domain/DailyQuestionService.kt`, `be/core/core-api/src/main/kotlin/com/devquest/core/api/scheduler/DailyMailScheduler.kt`, `be/core/core-domain/src/main/kotlin/com/devquest/core/domain/port/DailyQuestionGeneratorPort.kt`, `be/core/daily-core/build.gradle.kts`, `be/core/core-api/src/main/resources/application.yml`, `fe/src/features/tech-interview/components/DailyQuestionPage.tsx`, Stage A·B·C · 재판정 `아래 "기각한 선택지" G-2(b) 2026-08-18 부분 재채택`

크론은 1개로 유지하고 메서드 안에서 게이트를 둘로 나눈다: ①콘텐츠 생성(유저 수·`MAIL_ENABLED`
무관) ②메일 발송. 크론 자체를 쪼개지 않은 이유는 **실행 순서 경합** — 별도 크론이면 발송이 생성보다
먼저 돌 수 있다. (구현 `1bf075a`)

> 🔄 **2026-08-18 부분 재채택 — 읽기 경로에 한해 (b)를 좁게 되살렸다.**
> 위 결정은 *"생성은 크론이 한다"* 를 함의했고, 그 결과 `GET /api/v1/daily-question` 이
> **매일 00:00~09:00(하루 9시간) 404** 였다. 이제 읽기 시 **뱅크에서만** 생성한다.
> **AI 폴백은 여전히 크론 전용이다** — 그래서 아래 기각 사유 두 개가 성립하지 않는다.
> <!-- verify: be/core/daily-core/src/main/kotlin/com/devquest/core/domain/DailyQuestionContentService.kt ~ ensureTodayQuestionFromBank -->

### 기각한 선택지 (재론 방지)

**G-1**: (a)Fly 3앱 — 설계의 NetworkPolicy 전제가 통째로 무효 / (b)EKS 완전체 — destroy-after-use라
상시 서비스 불가, 크레딧 만료 후 갈 곳 없음 / (c)혼합 — 격리 구현 2벌, 드리프트 1순위.
> 🔑 셋 다 *"상시 환경에도 분리를 올린다"* 를 전제한 게 공통 함정이었다. 확정안은 그 전제를 버린다.

**G-2**: ~~(b)요청 시 생성 — 첫 요청자가 AI 지연을 다 먹고 동시 요청 시 중복 과금~~ /
(c)시드 결정론 — AI 호출 0으로 가장 단순하나 *"매일 새 문제"* 라는 제품 성격이 바뀐다.

> 🔄 **(b) 부분 재채택 — 2026-08-18, 읽기 경로 + 뱅크 전용에 한해** (D-005 참조).
> 기각 사유 두 개는 **AI 호출에 붙어 있었다.** 읽기 경로에서 AI 를 빼면 둘 다 성립하지 않는다:
>
> | 기각 사유 | 뱅크 전용에서는 |
> |---|---|
> | 첫 요청자가 AI 지연을 다 먹는다 | AI 를 안 부른다 → 지연 없음 (DB 조회 2회 + INSERT 1회) |
> | 동시 요청 시 중복 과금 | 과금 자체가 없다. 동시 요청이 겹쳐도 UNIQUE 가 정리하고 진 쪽은 재조회 |
>
> ⚠️ **이 판정은 아래 전제 위에 서 있다. 전제가 바뀌면 재판정하라** — 그래서 실측 근거를 남긴다:
> - prod 는 `transport: inprocess` 라 `read-timeout-ms` 가 적용되지 않는다 = **AI 호출에 타임아웃이 없다**
>   <!-- verify: be/core/core-api/src/main/resources/application.yml ~ transport: inprocess -->
> - `GET /api/v1/daily-question` 은 `permitAll` + **레이트 리밋 없음**(인터셉터는 `/evaluate`·`/explain` 에만)
>   <!-- verify: be/core/core-api/src/main/kotlin/com/devquest/core/api/config/WebMvcConfig.kt ~ addPathPatterns\("/api/v1/daily-question/explain"\) -->
>
>   ⚠️ **이 마커는 절반만 잠근다 — 도구의 한계를 정직하게 적는다.** `check-design-integrity.sh` 는
>   정규식의 **존재**만 단언할 수 있고 **부재는 단언할 수 없다.** 그래서:
>   - ✅ 잡는다: 누가 `/explain` 을 **bare 경로 `/api/v1/daily-question` 으로 바꾸면**
>     (= GET 에 레이트 리밋이 걸리면) 이 정규식이 깨져 CI 가 막는다
>   - ❌ 못 잡는다: 기존 줄을 그대로 두고 **새 인터셉터 등록을 추가**하는 경우
>
>   후자는 기계로 못 막으므로 **사람이 본다.** `WebMvcConfig.addInterceptors` 에 손대면
>   이 결정(D-005)을 다시 읽어라. (같은 한계가 `design-change-procedure.md` 의
>   *"검사기가 잡는 것 / 못 잡는 것"* 표에 이미 적혀 있다 — 새 사실이 아니라 적용 사례다)
> - UNIQUE 는 `save()` 만 dedup 한다 → **저장은 수렴해도 AI 비용은 수렴하지 않는다**
>
> 🔑 **AI 폴백을 읽기 경로에 넣고 싶어지면 여기부터 읽어라.** 위 3개가 그대로면 답은 여전히 "안 된다"다.

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
| 10 | ~~**HTTP 어댑터 인프라가 core-api 안에 있다.** daily가 ai-api를 부르려면 공유 승격 필요~~ → ✅ **해소 (2026-08-26, #393)**. `clients:client-ai-http` 로 추출됨(어댑터 15개 + `BaseAiHttpAdapter` + `AiApiRestClientBuilder`). *"공유 승격"* 은 이미 끝난 일이다 | `clients/client-ai-http/src/main/kotlin/com/devquest/client/ai/http/` |
| 11 | **`AiTransportConfig`(전송 스위치)가 core-api 전용** → daily는 태어날 때부터 HTTP-only. ⚠️ 어댑터 **클래스**는 더 이상 core-api 소유가 아니다(#10 해소) — core-api 에 남은 것은 **배선(`@Bean`)** 이다. 🔴 **B-2b 에서 드러난 함정**: 그 배선의 `@ConditionalOnProperty(havingValue="http")` 를 복사하면 core-api 엔 있는 인프로세스 폴백이 daily-api 엔 없어 **`TechInterviewPort` 0개 → 컨텍스트 붕괴**. daily-api 는 **무조건 등록**한다 | `AiTransportConfig.kt`, `daily-api/.../config/DailyAiConfig.kt` |
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
| **31** | **ai-api는 한 번도 배포된 적이 없다.** `k8s/base/`에 매니페스트 0건 → "분리는 이미 검증됐다"는 빌드 수준이지 런타임 수준이 아니다. 🔴 **2026-08-28 갱신: `daily-api`도 같은 상태다** — `k8s/base/` = `core-api.yaml`·`postgres.yaml`·`postgres-static.yaml` 셋뿐. 게다가 daily-api 는 **ECR 레포조차 없다**(`0-bootstrap/variables.tf:107` = `["core-api","ai-api"]`) | `k8s/base/`, `infra/aws-eks/0-bootstrap/variables.tf` |
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
      `design-change-procedure` 대상이 아니다.
      ~~**daily-api가 실제로 생기는 Stage C에서 필요해진다.** ⚠️ 착수 시 `V13`의 `FROM daily_mail_log`(#21) 교차 의존을 함께 풀어야 한다.~~

      > 🔄 **정정 — 방향이 반대였다 (2026-08-28 감사, 근거 4개).**
      > **지금 시행하면 Stage C 가 깨진다.** 공유 스키마는 Stage C 의 장애물이 아니라 **전제**다.
      >
      > | # | 근거 | 파일 |
      > |---|---|---|
      > | 1 | daily-api prod 는 `ddl-auto: validate` 로 **db-core 엔티티 15개 전부**를 기동 시 검증한다. daily 테이블만 다른 스키마로 옮기면 **validate 실패로 기동 불가** | `daily-api/src/main/resources/application-prod.yml`(함정 ⑦ 주석) |
      > | 2 | 마이그레이션 주체가 **core-api 하나로 고정**(열린 결정 ①). daily-api 는 `migrate-on-startup` 을 의도적으로 안 켠다 → **daily 전용 스키마를 만들 주체가 존재하지 않는다** | 두 앱의 `application-prod.yml` |
      > | 3 | `V13:40` 의 백필이 `FROM daily_mail_log`(V6, **core-api 모듈 소유**) — 둘 다 `public` 이라서 동작한다. 스키마를 가르면 교차 스키마 참조가 되어 `search_path` 없이는 깨진다 | `db-core/.../V13__create_daily_question_content.sql:40`, `core-api/.../V6__daily_mail_log.sql:1` |
      > | 4 | daily-api 가 읽는 `daily_question_content`(V13)·`tech_question_bank`(V10·V11) 는 전부 db-core 소유이고 core-api 가 `public` 에 적용한다. `core-api-db` Secret 4키를 그대로 `envFrom` 하면 접속된다 → **공유 스키마에서 e2e 가 성립한다** | `k8s/eso/externalsecret-db-incluster.yaml` |
      >
      > 🔑 **실제 만기 시점 정정**: *"daily-api 가 생길 때"* 가 아니라 **"DB 를 물리 분리하거나 daily-api 가 자체 마이그레이션을 소유할 때"** 다.
      > 설계 `:60` 의 🔴확정(*"daily=자체 스키마/테이블"*)은 **Stage C 를 통과한 뒤에도 미구현으로 남는다** — #26 이 지적한 *"확정했으나 코드 0줄"* 그대로다.
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

| Task | 내용 | 완료 |
|---|---|---|
| **A** | 읽기 경로 **뱅크 전용 lazy 생성** (G-2(b) 부분 재채택). 하루 9시간(00:00~09:00) 404 해소 | 2026-08-21 · PR #387·#388·#391 |
| **B-1** | `core:daily-core` 추출 — 로직만. 원장 L-27 해소(`DailyQuestionGeneratorPort` 신설) <!-- verify: be/core/daily-core/build.gradle.kts --> | 2026-08-22 · PR #392 |
| **B-2a** | `clients:client-ai-http` 추출 — AI HTTP 전송(어댑터 15개 + `BaseAiHttpAdapter`). ⚠️ 착수 시 범위를 **10배 오판**했다(상속을 안 봄) | 2026-08-26 · PR #393 |
| **B-2b** | `core:daily-api` 앱 신설 — 조립 + 단독 기동. Blindspot Pass 가 **계획에 없던 파괴 경로 3개**를 잡았고(공유 `ddl-auto: create-drop` 상속 / `@EntityScan` 0건 / 전송 스위치 조건 복사), 빌더가 **4번째**(Flyway 가 두 모듈에 분산 → `repair()` 가 core-api 를 영구 부팅 불가로) 를 자체 발견 <!-- verify: be/core/daily-api/src/main/kotlin/com/devquest/daily/DailyApiApplication.kt --> | 2026-08-27 · PR #395 |

---

## 🚧 Stage C 착수 블로커 (2026-08-28 감사 — 실측)

> 근거는 전부 레포 안의 코드·문서다. **클러스터는 꺼져 있었다**(과금 $0).
> 추측과 실측을 구분해 적었다 — `미확인` 표시가 붙은 것은 Stage C 착수 시 **실제로 재야 한다**.

| # | 블로커 | 근거 | 성격 |
|---|---|---|---|
| **C-1** | ✅ **해소 (2026-08-28, #397)** — ~~🔴 daily-api 에 헬스 엔드포인트가 없다.** actuator 의존 0건이고 전이 경로도 없다(`daily-core`→starter+tx, `client-ai-http`→restclient+jackson, `db-core`→data-jpa+json). `core-api.yaml` 을 복제하면 startupProbe 30회 실패 후 컨테이너가 죽는다 | `be/core/daily-api/build.gradle.kts` | **BE 코드 변경 + 이미지 재빌드** |
| **C-2** | ✅ **해소 — D-008 (스텁으로 대체)**. ~~🔴 `ANTHROPIC_API_KEY` 가 인프라 어디에도 없다.~~ `devquest-eks/app` 시크릿의 키는 `JWT_SECRET`·`GITHUB_CLIENT_ID`·`GITHUB_CLIENT_SECRET` 3개뿐. `client-ai-anthropic.yml` 은 `${ANTHROPIC_API_KEY:}`(빈 기본값)이라 **부팅은 성공하고 AI 호출 시점에 실패**한다 — CrashLoop 가 아니라 런타임 실패라 진단이 늦다 | `infra/aws-eks/2-cluster/secrets.tf`, `be/clients/client-ai/src/main/resources/client-ai-anthropic.yml` | **결정 필요** (아래 ⚠️) |
| **C-3** | ✅ **해소 (2026-08-30, #399)** — ECR 레포 `devquest/daily-api` 생성 확인(`Apply complete! 2 added`), `ecr-push.yml` `options` 에 추가. ~~🔴 daily-api ECR 레포 부재 + 빌드 경로 없음.** `ecr_repositories` 기본값이 `["core-api","ai-api"]` 이고 `terraform.tfvars` 에 override 가 없다. `ecr-push.yml` 은 `options` 에 daily-api 가 없고, **PR 트리거엔 `inputs` 가 없어 항상 `core-api` 로 폴백**한다 | `infra/aws-eks/0-bootstrap/variables.tf`, `.github/workflows/ecr-push.yml` | 인프라 + 워크플로 |
| **C-4** | ✅ **해소 — D-009 (t4g.medium 상향, `variables.tf` 반영 완료 08-31)**. ~~🔴 노드 용량.~~ 베이스라인 10/11(coredns=1 반영) → JVM 2개 추가 시 **12 > 11**. 🔴 **메모리가 먼저 막는다** — Stage 3a 실측 스케줄러 메시지가 `Insufficient memory, Too many pods` **둘 다**였다. requests 합 추정 512Mi×3 + 256Mi = **1792Mi**, limits 합 **3212Mi** (물리 2GiB 초과) | `docs/eks-migration-log.md:1079`, `k8s/base/core-api.yaml`, `k8s/base/postgres.yaml` | 비용 결정 |
| **C-5** | ✅ **해소 (2026-08-31)** — ①vpc-cni `enableNetworkPolicy = "true"`(#402, 스키마 실측: **문자열**) ②`k8s/base/networkpolicy-ai-api.yaml` 신설 — `ai-api` 에 `core-api`·`daily-api` 만, **ingress-only**, `namespace: default` 명시, TCP/8081 로 포트 제한. 🔑 **fail-open 3경로를 ruby YAML 파싱으로 기계 검증**: 정책 `podSelector` ↔ `ai-api.yaml` 의 **template 레이블** 일치 · `namespace` 명시 · `egress` 키 부재. ⚠️ **"막는다"의 증명은 아직 없다** — 클러스터가 떠야 한다. 차단/허용 쌍 검증 절차를 `k8s/README.md §4.1` 과 매니페스트 주석에 적어뒀다(파드 슬롯 0 소모 — 임시 파드 대신 `postgres-0` 에 exec) | `2-cluster/addons.tf`, `k8s/base/networkpolicy-ai-api.yaml` | 해소 |
| **C-6** | ✅ **해소 — 충돌이 아니라 범위 차이였다 (2026-08-28 판정).** `k8s/README.md` 의 Ingress 금지와 설계 `:213` 의 *"3 Deployment + Ingress 라우팅"* 은 **다른 것의 완료 조건**이다 — 전자는 **Stage C**, 후자는 **에픽 전체**. Stage C 완료 기준(`:58`)은 Ingress 를 요구하지 않는다. → **Stage C 는 `kubectl port-forward` 로 e2e 를 검증하고, Ingress 는 Phase 3 로 남긴다.** 비용 규율(destroy 후 ALB 잔존 과금)이 학습 단계에서 우선한다 | `k8s/README.md`, `specs/...design.md:205,213` | 판정 완료 |

### 🔴 감사에서 **새로 발견된** 블로커 (2026-08-31, NetworkPolicy Blindspot Pass)

| # | 블로커 | 근거 | 성격 |
|---|---|---|---|
| **C-7** | ✅ **해소 (2026-08-31, #403)** — `/health`(상수, `text/plain`) 컨트롤러 + `management.endpoint.health.group.readiness.include: **ping**` 추가. 🔑 **`daily-api` 의 `db,ping` 을 복사하면 안 된다는 것이 실측으로 확인됐다** — ai-api 는 `db-core` 미의존이라 `NoSuchHealthContributorException: Included health contributor 'db' in group 'readiness' does not exist` 로 **컨텍스트 로드 자체가 실패**한다(조용히 무시되는 게 아니다. QA 가 환경변수 오버라이드로 재현). ⚠️ **`application-prod.yml` 은 의도적으로 만들지 않았다** — ai-api 의 **유일한 실행 환경이 EKS 학습 클러스터**이고(Fly 는 core-api 만 배포), 거기서 `DEBUG` 로그는 결함이 아니라 **필요한 것**이다(3서비스 첫 기동의 유일한 관측 수단). 형식적 일관성을 위해 `INFO` 로 낮추는 파일을 만드는 것은 학습 목표에 역행한다. 🔑 재검토 트리거: ai-api 가 실제 배포 대상을 갖게 되면 즉시. 📌 남은 tech-debt 는 원장 **L-39**(readiness 가 liveness 와 등가 — Judge0 붙으면 인디케이터 추가) | `be/core/ai-api/src/main/{kotlin,resources}/` | 해소 |
| **C-8** | ✅ **해소 (2026-08-31)** — `daily-api.yaml` 에 `DEVQUEST_AI_HTTP_BASE_URL=http://ai-api:8081` 주입. `DailyAiConfig` 의 기본값 `http://localhost:8081` 이 살아 있는데 daily-api 자신의 포트는 8082 라 **자기 자신을 호출**하게 되는 문제였다. ⚠️ 증상(연결 실패)이 **NetworkPolicy 차단과 구별되지 않아** 정책으로 오귀속되기 쉬운 자리 — 매니페스트 주석에 그 사실을 남겼다 | `k8s/base/daily-api.yaml` | 해소 |

> 🔑 **C-7·C-8 이 위험한 이유는 실패가 NetworkPolicy 와 구별되지 않기 때문이다.**
> 셋 다 증상이 *"파드가 죽거나 연결이 안 된다"* 로 같다. 같은 세션에 정책을 막 걸어놨다면
> **원인을 정책으로 오귀속**하고 엉뚱한 곳을 파게 된다 — 그것도 과금 구간 안에서.
> → **C-7·C-8 을 먼저 닫고**, 그다음에 정책을 건다.

> 📌 **C-9 (기록만)**: 정책의 *"core-api 허용"* 규칙은 **이번 세션에 한 번도 실행되지 않는다.**
> core-api 는 `transport: inprocess` 라 클러스터에서 ai-api 를 HTTP 로 부르지 않는다
> (`AiHttpClientConfig` 의 `havingValue = "http"` 게이트). daily-api 쪽만 검증되고 core-api 쪽은
> **작동한다고 믿지만 통과시켜본 적 없는 규칙**으로 남는다 → 매니페스트 주석에 명시할 것.
> 안 그러면 Phase 3 에서 `transport=http` 를 켰을 때 여기서 막히고 원인을 못 찾는다.

### 블로커가 **아닌** 것 (오해 방지)

| 항목 | 판정 |
|---|---|
| `be/Dockerfile` 의 `ARG SERVICE` | ✅ **그대로 동작한다.** `settings.gradle.kts` 가 `core:daily-api` 를 include 하므로 `--build-arg SERVICE=daily-api` 로 경로 규칙을 만족한다 |
| `be/Dockerfile:35` 의 `EXPOSE 8080` 고정 | ⚪ **블로커 아님**(추정, 표준 Docker 동작). `EXPOSE` 는 메타데이터일 뿐 포트 바인딩이 아니다 — K8s `containerPort` 를 8081/8082 로 맞추면 통신은 정상. **오해 유발 요소이지 차단 요소가 아니다** |
| ESO 시크릿 분할 | ✅ **불필요.** `core-api-db` 의 4키(`DB_HOST`·`DB_NAME`·`DB_USERNAME`·`DB_PASSWORD`)가 daily-api 요구와 **정확히 동일**하다. `core-api-app`(JWT/OAuth)은 daily-api·ai-api 둘 다 불필요. 🟡 다만 이름이 `core-api-*` 라 3서비스가 공유하면 **소유권이 흐려진다** |
| 열린 결정 ②(스키마 분리) | ⚪ **블로커 아님. 오히려 지금 하면 깨진다** — 위 §열린 결정 ② 정정 참조 |

### ⚠️ C-2 는 **원칙 충돌**이라 사용자 결정이 필요하다

Stage C 완료 기준(*"무로그인으로 오늘의 질문 → **AI 설명**까지"*)은 실제 Anthropic 호출을 요구한다.
그런데 `secrets.tf` 가 *"학습 클러스터에 prod 크리덴셜을 넣지 않는다"* 를 **명시적 원칙**으로 못박았고,
같은 파일이 **더미 Grafana 키를 넣었다가 실제 스택에 인증 시도가 새어나간 사고**를 기록해뒀다.
→ 계획서·설계서 어디에도 이 항목이 없다. **완료 기준을 좁히거나, 키 주입 방식을 정하거나 둘 중 하나다.**

### 🟡 Stage C 착수 시 **실제로 재야 하는** 값 (레포에 기록 0건)

| 값 | 왜 | 명령 |
|---|---|---|
| 노드 `Allocatable.memory` | C-4 의 메모리 벽 판정이 전부 **추정**이다 | `kubectl describe node` |
| t4g.medium 온디맨드 단가 | 일지 `:1086` 의 *"$0.13→$0.16"* 은 **근거 미기재**. small $0.0208 의 2배로 계산하면 ≈$0.149/h 로 약 $0.01 어긋난다 | `aws ec2 describe-spot-price-history` 아님 — 온디맨드 가격표 API |
| t4g.medium 파드 상한 | *"상한 17"* 도 근거 미기재. small 의 11 은 `describe-instance-types` 로 **2회 실측**했으나 medium 은 0건 | `aws ec2 describe-instance-types` |
| ai-api·daily-api 의 힙·Metaspace 실사용 | Dockerfile 의 JVM 플래그는 **Fly 512MB 예산**에서 나왔고 실측 근거는 **core-api prod 하나뿐**(사용 42MB/커밋 117MB). 3서비스에 동일 적용된다 | 배포 후 `jcmd` / actuator metrics |

---

## 다음 (이 계획 이후)

**Phase 3**: EKS 배포 토폴로지, `client-ai` 컴파일 의존 제거, 분산 트레이싱.
⚠️ Stage C 착수 전 확인할 것 — vpc-cni에 `enableNetworkPolicy` 필요(현재 맨몸) ·
~~t4g.small 파드 상한 11인데 Stage 3a에서 이미 11/11~~ → **정정 (2026-08-28)**: Stage 3a 의 11/11 은 **Stage 3b 에서 `coredns` replicaCount 를 1로 낮추기 전** 숫자다(`2-cluster/addons.tf`). 현재 `.tf` 기준 베이스라인은 **10/11, 여유 1** → JVM 2개(ai-api·daily-api) 추가 시 **12 > 11, 정확히 1칸 부족**. 🔴 **그런데 파드 상한보다 메모리가 먼저 막는다** — Stage 3a 실측 스케줄러 메시지가 `Insufficient memory, Too many pods` **둘 다**였다(`docs/eks-migration-log.md:1079`). 상세는 §Stage C 착수 블로커.
