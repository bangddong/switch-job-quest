# 메모리 재산정 — 부하 상태 측정 계획 ($0 준비)

> **이 문서의 지위**: 09-04 유료 세션이 남긴 *"다음 착수 지점 = 부하 상태에서 메모리 재측정 →
> requests 재산정 → D-011(3대) 다시 열기"* 를 **클러스터 없이 실행 가능한 절차로** 바꾼 것.
> 절차·판정 기준·실패 모드를 **과금 전에** 확정한다.
>
> 🔑 근거: 09-04 실측 — 예상 65분/$0.19 → 실제 33분/$0.103.
> *"과금 구간에서 무엇을 **하지 않을지**가 비용을 정한다."*

---

## 0. 요약 — $0 조사가 계획을 바꿨다

착수 전 검산에서 **계획서가 적어둔 측정 수단 두 개가 성립하지 않음**이 드러났고,
더 중요하게는 **문제의 방향 자체가 바뀌었다.**

| # | 발견 | 확도 |
|---|---|---|
| **F-1** | 런타임 이미지에 **`jcmd` 가 없다** (`eclipse-temurin:21-jre-alpine` = JRE). 계획서 `:617` 의 *"배포 후 `jcmd` / actuator metrics"* 중 **jcmd 경로는 존재하지 않는다** | 🔴 실측 |
| **F-2** | **actuator 메트릭 경로가 앱마다 다르다.** `ai-api`·`daily-api` 는 `exposure.include: health` 뿐이고 micrometer 레지스트리 자체가 없다 → `/actuator/prometheus` **없음**. `core-api` 만 `monitoring.yml` 경유로 **있다** | 🔴 실측 |
| **F-3** | 그 `core-api` 조차 `SecurityConfig` 가 `/actuator/**` 를 `127.0.0.1 / ::1 / fdaa::/16` 로 제한한다. **파드 안에서 localhost 로만** 접근 가능(prod 외부 GET = **403** 실측). `fdaa::/16` 은 **Fly 사설망**이라 EKS 에선 아무것도 열지 않는다 | 🔴 실측 |
| **F-4** | 🔴 **`limits: 900Mi` 가 힙 상한을 정하고 있다.** `MaxRAMPercentage=35` × 900Mi = **MaxHeap 316Mi**. Dockerfile 주석의 *"최대 약 179MB"* 는 **Fly 512MB 기준**이고 **EKS 에는 해당되지 않는다** | 🔴 실측 |
| **F-5** | 예약 상한 합 = 힙 316 + Metaspace 160 + CodeCache 96 = **572Mi > `requests: 512Mi`**. 즉 requests 는 JVM 설정과 **무관하게** 정해진 값이다 | 🔴 산술 |
| **F-6** | 레이트 리밋이 **IP당·하루** 단위다 — `explain` **5회**, `evaluate` **1회**. 3서비스를 태우는 유일한 경로(`/explain`)가 **파드 하나당 5요청**에서 막힌다. **아무 대비 없이 부하를 걸면 429 만 측정하게 된다** | 🔴 실측 |
| **F-7** | GC 가 **SerialGC** 다 (컨테이너가 server-class 문턱 1792MB 미만). G1 전제의 튜닝 감각이 통하지 않는다 | 🔴 실측 |
| **F-8** | cgroup v2 에 **`memory.peak` 이 있다** — 부하 중 최고점이 커널에 남는다. 09-04 는 `memory.current` 만 읽었다(순간값) | 🔴 실측 |

### 🔴 방향 전환 — "requests 를 줄인다" 가 아니라 "limits 가 먼저다"

09-04 결론은 *"실사용 842Mi vs requests 1792Mi → 줄일 수 있다"* 였다. 그런데 **F-4** 가 그 위에 선다:

```
현재:  limits 900Mi  →  MaxHeap 316Mi  →  JVM 이 그만큼 자랄 권리를 갖는다
Fly:   총 512MB      →  MaxHeap 179Mi  →  prod 실측 사용 42MB / 커밋 117MB
```

**`core-api` 가 EKS 에서 idle 337Mi 를 쓴 것은 우리가 900Mi 를 줬기 때문일 수 있다.**
`limits` 를 낮추면 힙 상한이 **같이** 내려가므로, `requests` 만 만지는 것은 절반짜리 조작이다.

| `limits` | `MaxHeap` (35%) | GC |
|---|---|---|
| 384Mi | 136Mi | Serial |
| **512Mi** | **180Mi** | Serial |
| 640Mi | 224Mi | Serial |
| 768Mi | 270Mi | Serial |
| **900Mi (현행)** | **316Mi** | Serial |

> `512Mi → 180Mi` 가 Dockerfile 주석의 *"약 179MB"* 와 일치한다 — 그 주석이 **512MB 예산에서
> 쓰였다는 직접 증거**다. 같은 이미지가 EKS 에서는 **1.77배**의 힙을 갖고 돌아간다.

**⚠️ 반증 가능성 (이 문서가 틀릴 수 있는 자리)**: Metaspace·CodeCache 는 *예약* 상한이지 커밋이 아니다.
572Mi 를 **실제로 쓴다는 주장이 아니다.** 주장은 *"requests 512Mi 는 어떤 실측·설정에서도 유도되지
않았다"* 뿐이다. 실제 커밋량은 측정 대상이다.

---

## 1. 측정 수단 — 무엇으로 잴 수 있나 (F-1~F-3 반영)

| 대상 | 컨테이너 총량 | 힙 | 영역별(Metaspace·CodeCache·direct) |
|---|---|---|---|
| `core-api` | cgroup ✅ | GC 로그 ✅ | **`/actuator/prometheus` ✅** (파드 내부 localhost 한정) |
| `ai-api` | cgroup ✅ | GC 로그 ✅ | ❌ |
| `daily-api` | cgroup ✅ | GC 로그 ✅ | ❌ |
| `postgres` | cgroup ✅ | — | — |

### 🔑 비대칭을 **없애지 않고 이용한다** (채택)

`ai-api`·`daily-api` 에 prometheus 를 열려면 **BE 코드 변경 + ECR 재빌드 2건**이 필요하다.
대신 **세 앱이 같은 베이스 이미지·같은 JVM 플래그**라는 점을 쓴다:

1. `core-api` 에서 **영역별 분해**를 얻는다 (`/actuator/prometheus`, 변경 0)
2. 같은 파드의 **cgroup 총량**과 대조해 **prometheus 에 안 잡히는 네이티브 오버헤드**를 산출한다
   → `총량 − (힙커밋 + Metaspace + CodeCache + 스레드스택)` = 나머지 네이티브
3. `ai-api`·`daily-api` 는 **총량만** 재고, ②의 구조를 **가설로 적용**한다

> 🟡 **이 전이는 가설이다.** 세 앱은 의존 트리가 다르므로 Metaspace 는 다를 수밖에 없다
> (`ai-api` 는 Spring Security 도 `db-core` 도 없다). **총량 결론은 실측이고, 분해 설명만 가설이다** —
> 문서에 그렇게 구분해 적는다. 이 구분을 흐리면 이 트랙이 반복해 온 *"추정이 실측으로 승격되는"* 사고가 된다.

### 폐기한 대안

| 대안 | 왜 안 쓰나 |
|---|---|
| `jcmd VM.native_memory` | **바이너리가 없다** (F-1). JDK 이미지로 바꾸면 이미지가 커지고 **Fly prod 까지 바뀐다**(같은 Dockerfile) |
| `-XX:StartFlightRecording` + `jfr` | `jfr` 은 **있다**. 하지만 JVM 플래그 변경 = **이미지 재빌드 3건**. 이번 목적(총량 산정)에 과하다 |
| `kubectl top` | **metrics-server 가 없다** (09-04 확인). 설치하면 파드 슬롯·메모리를 먹고 **측정 대상을 오염**시킨다 |

---

## 2. 부하 설계 — F-6 이 지배한다

### 문제

`/api/v1/daily-question/explain` 이 **3서비스를 전부 태우는 유일한 경로**인데,
IP당 **하루 5회**에서 429 로 막힌다. `evaluate` 는 **1회**.
**대비 없이 부하를 걸면 6번째 요청부터는 인터셉터만 측정하게 된다.**

### 채택 — `SPRING_APPLICATION_JSON` 으로 세션 한정 상향

```yaml
# k8s/base/daily-api.yaml 에 부하 세션에만 추가 (이미지 재빌드 0)
- name: SPRING_APPLICATION_JSON
  value: '{"devquest.rate-limit.daily-explain.capacity":100000,"devquest.rate-limit.daily-evaluate.capacity":100000}'
```

🔑 **`SPRING_APPLICATION_JSON` 을 쓰는 이유 = relaxed binding 추측을 피하려고.**
`DEVQUEST_RATELIMIT_DAILYEXPLAIN_CAPACITY` 같은 환경변수 형태는 대시 처리 규칙을 **틀리면
조용히 무시된다** — 옛 용량이 그대로 살아 있는데 아무 에러도 안 난다. 이 레포가 반복해서 데인
*"부재가 성공과 똑같이 생긴"* 실패다. JSON 은 **프로퍼티 이름을 그대로** 쓴다.

✅ **키 일치 확인 (QA 검토 중 코드로 확정)** — `DailyExplainRateLimitBucketStore` 는
`@Value("${devquest.rate-limit.daily-explain.capacity:5}")` 로 받는다
(`DailyExplainRateLimitInterceptor.kt:22`). `@Value` 플레이스홀더는 relaxed binding 이 아니라
**문자 그대로 매칭**이므로, 위 JSON 의 키와 정확히 같다 → 이름이 틀릴 위험은 **없다**.
🔑 그래도 아래 6회 검사는 **유지한다** — 이름 말고도 틀릴 자리가 있다(파드가 env 없이 재시작,
JSON 파싱 실패, 롤아웃 미완). 검사가 막는 것은 *"이름 오타"* 가 아니라 *"적용 안 됨"* 이다.

> ⚠️ **그래도 검증한다 — 설정은 "걸었다"가 아니라 "먹었다"를 봐야 한다.**
> **부하 전에 `explain` 을 6회 호출한다. 6번째가 200 이면 상향이 먹은 것이고, 429 면 안 먹은 것이다.**
> (5회까지는 상향 여부와 무관하게 성공하므로 **6번째가 유일한 판별점**이다.)
> 이 검사가 실패하면 부하를 걸지 말고 원인부터 본다 — 429 를 측정해봐야 아무 뜻이 없다.

### 부하 파드는 **별도 파드**여야 한다

측정 대상 컨테이너 안에서 `wget` 루프를 돌리면 **그 프로세스의 메모리가 측정값에 섞인다.**
09-04 의 e2e 는 `daily-api` 파드 안에서 실행했는데, 그건 도달성 확인이라 상관없었다.
**측정 중에는 안 된다.**

- 파드 슬롯: 노드당 11, 현재 6 사용 → 여유 충분
- `busybox` 는 ~2MB, arm64 멀티아치
- ⚠️ **부하 파드는 `ai-api` 를 직접 못 때린다** — NetworkPolicy 가 `core-api`·`daily-api` 만 허용한다.
  이건 결함이 아니라 **C-5 가 살아 있다는 증거**다. `ai-api` 부하는 `daily-api/explain` 경유로만 건다.

### 부하 형상

| 항목 | 값 | 근거 |
|---|---|---|
| 워밍업 | **모든 엔드포인트를 1회씩** | 🔑 **Metaspace 는 요청 수가 아니라 "밟은 코드 경로"로 자란다.** 만 번 GET 을 때려도 `explain` 을 안 밟으면 그 클래스는 안 올라온다 |
| 동시성 | **20** (백그라운드 `wget` 루프 20개) | 가상 스레드(`spring.threads.virtual.enabled=true`)라 스레드 스택은 힙에 있다. 20 은 커넥션풀·핸들러를 깨우기에 충분하고 학습 클러스터를 죽이지 않는 수준 |
| 지속 | **3분** | 힙이 정상상태에 들고 풀 GC 가 최소 1회 돌 시간. 과금 구간 예산 안에서 |
| 대상 | `daily-api` GET(무제한) + `explain`(상향 후) + `core-api` GET | `evaluate` 는 AI 스텁 경로가 `explain` 과 겹쳐 생략 |

---

## 3. 절차 (과금 구간 — 목표 12분)

> 전제: Stage C 와 동일한 상태까지 기동 완료(`node_desired_size=3`, 3서비스 Ready).
> 이 문서는 **그 이후**만 다룬다. 기동 절차는 `docs/eks-tutorial-steps.md`.

| # | 단계 | 확인 |
|---|---|---|
| 1 | **idle 기준선** — `k8s/loadtest/measure-memory.sh baseline` | 4개 컨테이너 값이 다 찍힌다. 09-04 값(337/287/183/35)과 같은 자릿수면 정상 |
| 2 | `SPRING_APPLICATION_JSON` 추가 후 `daily-api` 롤아웃 | `kubectl rollout status` 성공, RESTARTS 0 |
| 3 | 🔑 **상향 검증** — `explain` **6회** | **6번째가 200.** 429 면 **중단하고 원인 조사** |
| 4 | `kubectl apply -f k8s/loadtest/loadgen.yaml` | 파드 Running |
| 5 | 워밍업 1회전 → **3분 부하** | 로그에 429/5xx 가 없어야 한다 |
| 6 | **부하 중 측정** — `measure-memory.sh sample` 30초 간격 | `memory.peak` 이 자란다 |
| 7 | 부하 종료 후 60초 대기 → **회복 측정** | 힙이 돌아오는가(SerialGC 는 반환이 인색하다 — F-7) |
| 8 | `core-api` **영역별** — 파드 안에서 `/actuator/prometheus` | `jvm_memory_used_bytes` 가 나온다 |
| 9 | `kubectl delete -f k8s/loadtest/loadgen.yaml` + `SPRING_APPLICATION_JSON` 원복 | **원복 잊으면 다음 세션이 상향된 채로 돈다** |

---

## 4. 판정 기준 — **지금 정한다** (과금 중 결정 금지)

측정이 끝나면 **아래 규칙을 기계적으로 적용**한다. 숫자를 보고 규칙을 만들면
원하는 결론이 나오게 규칙을 고르게 된다(09-04 D-011 이 피한 바로 그 함정).

### 4.1 용어

```
W_peak  = 부하 중 워킹셋 최고값 = max(memory.peak, max(sample(memory.current − inactive_file)))
W_idle  = 기동 후 무부하 워킹셋
```

> `inactive_file` 을 빼는 이유: cgroup `memory.current` 는 **페이지 캐시를 포함**한다.
> JVM 컨테이너는 캐시가 작아 차이가 미미하지만 **`postgres` 는 버퍼드 IO 라 크게 부풀어 오른다.**
> 회수 가능한 캐시를 requests 로 예약하면 노드 한 대를 캐시에 헌납하게 된다.
> (k8s 의 `working_set` 정의와 같은 식이다.)

### 4.2 규칙

| 대상 | 규칙 |
|---|---|
| **`limits`** | `limits_new = roundup64( W_peak × 1.35 )`, **하한 448Mi** — 힙 상한이 여기서 파생되므로(F-4) **먼저** 정한다 |
| **`requests`** | `requests_new = roundup32( W_peak × 1.15 )` |
| **불변식** | `requests_new ≤ limits_new` 이고 `limits_new ≥ W_peak × 1.2` |

- **1.15 (requests)**: Burstable QoS 는 **requests 초과분이 클수록 먼저 evict** 된다.
  정상 부하에서 requests 를 넘지 않는 것이 목적이므로 마진은 작아도 된다.
- **1.35 (limits)**: 초과하면 **OOMKill** 이다. 되돌릴 수 없는 실패라 마진을 더 준다.
- **하한 448Mi**: 🔴 **정정 (QA F-1). 처음에 384Mi 로 적었는데 그 근거가 틀렸고, 숫자도 틀렸다.**

  ~~그 아래면 MaxHeap 136Mi 인데 prod 실측 힙 **커밋이 117MB** 다. 여유가 19MB 뿐이라
  Metaspace 데스 스파이럴(2026-07-14 실제 사고) 재현 위험이 있다.~~

  **왜 틀렸나**: ①Fly(힙 상한 179Mi·실사용자 트래픽)의 수치를 EKS(384Mi 가정·부하테스트)에
  **환경을 건너뛰고** 갖다 붙였다 ②2026-07-14 사고는 **`-XX:MaxMetaspaceSize=160m` 이라는 고정값**에서
  났다. 그 값은 `limits` 와 **무관하게 항상 160m** 이므로 힙 여유 논증과 **인과관계가 없다.**

  **대신 구조적 부등식으로 바꾼다** — JVM 이 스스로 예약하는 상한이 컨테이너 상한을 넘으면 안 된다:

  ```
  0.35·L  +  MaxMetaspace(160Mi)  +  ReservedCodeCache(96Mi)  ≤  L
  ⇒  L ≥ 256 / 0.65 = 393.8Mi
  ```

  | `limits` | MaxHeap | 예약 합 | 여유 |
  |---|---|---|---|
  | **384Mi** | 134Mi | 390Mi | **−6Mi** 🔴 |
  | 394Mi | 137Mi | 393Mi | 1Mi |
  | **448Mi** | 156Mi | 412Mi | **36Mi** |
  | 512Mi | 179Mi | 435Mi | 77Mi |
  | 900Mi (현행) | 315Mi | 571Mi | 329Mi |

  🔑 **내가 고른 384Mi 는 부등식을 만족하지 않는다** — 예약만으로 이미 상한을 6Mi 넘는다.
  교차점 394Mi 는 여유가 1Mi 라 스레드 스택·GC 구조·직접버퍼가 들어갈 자리가 없다.
  → **64Mi 단위로 올려 448Mi** 를 하한으로 둔다.

  ⚠️ 이건 *예약* 기준이라 보수적이다(실제 커밋은 더 작다). 그래도 **하한**의 성격에는 맞다 —
  하한은 "여기 밑으로는 구성 자체가 모순" 인 선이지 "여기면 충분" 인 선이 아니다.

  🔑 **그리고 진짜 위험은 반대 방향에 있었다**: `MaxMetaspaceSize` 가 고정이므로
  **`limits` 를 낮출수록 Metaspace 가 예산에서 차지하는 비중이 커진다**(900Mi 에서 18%, 448Mi 에서 36%).
  prod 는 128m 으로 줄였다가 **실제로 Metaspace OOME** 를 맞았으므로(커밋이 128MB 를 넘었다는 실측),
  이 고정비는 작지 않다. `limits` 를 조일 때 조여지는 것은 **힙뿐**이다.

### 4.3 이 규칙이 산출할 수 있는 결론 — **셋 다 유효하다**

| 결과 | 그러면 |
|---|---|
| `requests_new` 합 < 현행 → **노드 2대 가능** | **D-011 을 다시 연다.** 재개 경로를 3대 → 2대로 |
| 합이 비슷 | requests 는 그대로 두고 **`limits` 만** 조인다. 힙 상한이 내려가 여유가 는다 |
| 부하 상태가 512Mi 를 **넘는다** | 🔴 **줄이는 게 아니라 늘려야 한다.** F-5 가 예고한 방향. **이 결과를 "실패" 로 취급하지 않는다** |

> 🔑 **세 번째 칸이 이 문서의 존재 이유다.** 09-04 는 idle 만 보고 *"2배 과다"* 라고 적었다.
> 부하를 주면 반대 결론이 나올 수 있고, **그때 규칙을 고치지 않으려고 미리 적어둔다.**

### 4.4 적용 대상 — Fly prod 는 건드리지 않는다

`requests`/`limits` 는 **k8s 매니페스트에만** 있다. `be/Dockerfile` 의 JVM 플래그는
**Fly prod 와 공유**되므로 이번 측정 결과로 **바꾸지 않는다**.
바꾸고 싶어지면 별도 판단 — prod 는 512MB 머신이고 여긴 학습 클러스터다.

---

## 5. 실패 모드 — 미리 적어둔다

| 증상 | 진짜 원인 후보 | 구분법 |
|---|---|---|
| `explain` 이 429 | 상향 미적용 | **3단계**에서 이미 걸러진다. 부하 중에 나오면 파드가 재시작해 env 가 빠진 것 |
| `ai-api` 메모리가 안 자람 | ①부하가 실제로 안 갔다 ②스텁이라 원래 가벼움 | `ai-api` 로그의 `[STUB] … 호출됨` **건수**를 센다. 0 이면 ① |
| `postgres` 가 limits 까지 참 | 페이지 캐시(정상) | `memory.stat` 의 `anon` 을 본다. `anon` 이 작으면 정상 |
| 부하 파드가 `ai-api` 에 못 붙음 | **NetworkPolicy — 정상 동작** | 결함이 아니다. `daily-api` 경유로만 건다 |
| 힙이 부하 후 안 줄어듦 | SerialGC 는 반환에 인색(F-7) | 결함 아님. **`W_peak` 기준이라 판정에 영향 없음** |

---

## 6. 이 문서가 닫는 것 / 열어두는 것

- ✅ **닫음**: 측정 수단(§1) · 부하 설계(§2) · 절차(§3) · 판정 규칙(§4) · 실패 모드(§5)
- 🔓 **열어둠**: 실제 숫자. 그건 클러스터가 떠야 한다
- 🔓 **열어둠**: `ai-api`·`daily-api` 에 prometheus 를 열지 여부 → **이번 측정 결과로 판단.**
  총량만으로 결론이 서면 안 연다(변경 0). 분해 없이는 설명이 안 되면 그때 연다

---

## 7. 검증 기록 — 이 준비물은 **돌려봤다**

> 이 레포는 *"지금은 배선 못 하지만 코드는 남겨두자"* 를 금지한다(빨간 깃발, L-24).
> 과금 구간에 들고 갈 스크립트가 한 번도 안 돌아본 것이면 그게 바로 그 항목이다.
> 클러스터 없이 돌릴 수 있는 데까지 **실제로 돌렸다.**

| 대상 | 방법 | 결과 |
|---|---|---|
| `measure-memory.sh` 의 컨테이너 리더 | 실제 컨테이너 2종(`21-jre-alpine`·`postgres:17-alpine`)에서 실행 | ✅ 두 이미지 모두 정상 출력. **사본을 만들지 않고** 스크립트에서 스니펫을 추출해 돌렸다 |
| `measure-memory.sh` 전 경로 | 가짜 `kubectl`(docker 로 위임) | ✅ 헤더·4행·`limit=512Mi`·`peak>current` |
| 실패 경로(파드 없음 / exec 실패) | 가짜 `kubectl` 이 빈 값·exit 1 반환 | ✅ **조용히 넘어가지 않고** 사유가 찍힌다 |
| `loadgen.yaml` 배선 | ruby YAML 파싱 | ✅ 볼륨↔ConfigMap 이름 일치, `load.sh` 존재 |
| `load.sh` 문법 | busybox `sh -n` (stdin) | ✅ — **단, 반증 먼저**(아래) |
| `code()` 상태코드 추출 | 실제 busybox httpd 서버 | ✅ `200→200` · `404→404` · **도달불가→`ERR`** · POST 동작 |

### 🔴 이 검증이 **자기 자신에게서** 잡아낸 것 2건

**① 첫 문법 검사는 거짓 통과였다.**
`-v /tmp/load.sh:/load.sh:ro` 로 마운트했는데 colima 가 그 호스트 경로를 VM 에 넘기지 않아
docker 가 **빈 디렉터리**를 만들었다. `sh -n` 을 디렉터리에 걸었더니 **exit 0** 이 나왔고
나는 `✅ 문법 OK` 를 출력했다. 컨테이너 안에서 확인하니 `wc -l` = `Is a directory`.
→ 마운트를 버리고 **stdin** 으로 넘기고, **일부러 깨진 스크립트(`fi` 누락)가 실패하는지**를
먼저 확인한 뒤에 진짜 검사를 돌렸다. **반증이 통과한 검사만 신뢰한다.**

**② `code()` 가 도달 불가 시 빈 문자열을 냈다.**
연결이 아예 안 되면(DNS 실패·NetworkPolicy 차단·타임아웃) `sed` 가 아무것도 못 뽑아 빈 칸이 찍힌다.
마지막 `sort | uniq -c` 집계에서 **빈 줄은 실패로 보이지 않는다** —
이 트랙이 반복해 온 *"부재가 성공과 똑같이 생긴"* 실패 그대로다. → `ERR` 로 이름을 붙였다.

### ⚠️ 여전히 못 돌려본 것 (정직하게)

- `kubectl exec` 실물 · 서비스 DNS(`daily-api:8082`) · NetworkPolicy 하의 실제 라우팅
- `SPRING_APPLICATION_JSON` 이 레이트리밋을 실제로 상향하는가
  → **그래서 §3-3 에 "6번째가 200 인가" 판별점을 넣었다.** 추측을 절차 안의 검사로 바꾼 것이지
  해소한 것이 아니다
- 20 동시성이 학습 클러스터에서 적정한가 (부하가 약하면 올리고, 5xx 가 나오면 내린다)
