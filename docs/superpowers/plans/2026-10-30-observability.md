# 관측 — ⑥ B8-18 창 관측 설계 + 관측 학습 트랙

> 상위: `2026-10-16-prod-eks-weekday-window.md` 「10 직전」 ⑥ · B8-18 · U-6 구현 ⓑ · 원장 L-78.
> 이 문서는 **⑥ 의 설계 결정**과, ⑥ 뒤에 이어지는 **관측 학습 트랙**(별도 트랙, 「10 직전」 아님)을 소유한다.
> 조사: 2026-10-30 읽기 전용 세션(main `9f22ab7` 기준). 코드 근거는 아래 「현황」.

## 결정 테이블 (2026-10-30, 사용자 *"A안으로 가고"* · *"추천대로 가자"*)

| 결정 | 선택 | 근거 · 기각 대안 |
|---|---|---|
| 관측 백엔드 | **Grafana Cloud 유지** (Mimir·Loki, 이후 Tempo) | 클러스터는 창마다 destroy → 클러스터 안 Prometheus/Grafana 는 저장소가 매일 사라진다. Fly 도 밖으로 보내야 해 수집처가 둘이 된다. CloudWatch·Datadog 은 Fly 가 이미 Grafana 라 전환 비용만 생기고 Datadog 은 유료. 사용자 목표 *"모니터링 역량도 늘리고 싶다"* — Grafana 스택은 OTel·PromQL 개념이 감춰지지 않아 학습에 맞다 |
| ⑥ 방식 | **A — push 유지, OTel 리소스 속성으로 플랫폼·인스턴스 분리** | B(클러스터 안 Alloy/OTel Collector 스크레이프) 기각: 새 차트·IRSA·노드 메모리, `SecurityConfig` 의 `/actuator/**` 127.0.0.1 제한을 풀어야 해 ⑤ actuator 판정과 정면 충돌, Fly 는 어차피 A 가 필요. → B 는 **학습 트랙 L-5** 로 따로 실습 |
| daily-api Grafana 키 | **전용 ExternalSecret 신설**(GRAFANA 키만) | `daily-api-edge` 확장은 이름-내용 불일치, `core-api-runtime` 공유는 Anthropic 등 불필요 키 노출(최소권한 위반) |
| 창 로그 | **core-api · daily-api 둘 다 Loki** | EKS prod 창엔 Fluent Bit 가 없어 destroy 시 로그 소실. L-4(트레이스↔로그 연결)의 전제 |
| ai-api AI 비용 지표 | **⑥ 에 포함** | 같은 메커니즘(`support:monitoring` 의존)이고, 빠지면 창 중 오늘의 질문 AI 비용이 대시보드에서 사라진다. ✅ 사용자 확정 10-30(*"ai-api도 ⑥에 포함하는 걸로 가자"*) |
| TASK-17 시점 | **⑥ 1단계 머지·Fly 배포 후** | 먼저 넣으면 EKS core-api 가 즉시 같은 리소스로 push → 충돌이 실제로 발생(아래 현황) |

## 현황 (조사 근거)

| 사실 | 근거 |
|---|---|
| `service.name` 리터럴 고정, `service.instance.id`·플랫폼 속성 없음 | `be/support/monitoring/.../OtlpMetricsConfig.kt` `"otlp.resourceAttributes" -> "service.name=devquest-api"` |
| 송출 앱은 core-api 하나 — daily-api·ai-api 송출 경로 0 | `core-api/build.gradle.kts` `project(":support:monitoring")`, daily-api·ai-api 미의존 |
| Fly·EKS core-api 는 같은 이미지·같은 `application-prod.yml` → **같은 리소스로 같은 스택에 push** | `core-api/application-prod.yml` `grafana.otlp` |
| 대시보드가 job/instance 필터 없이 합산 → 섞이면 가짜 급증·리셋 | `grafana/ai-metrics-dashboard.json` `max_over_time(...) - min_over_time(...)` |
| Loki 라벨 고정 → Fly·EKS 로그 구분 불가. daily-api 는 Loki 경로 없음 | `logback-spring.xml` `app=devquest-api,level=%level` |
| EKS 창에 Fluent Bit 없음 | `k8s/gitops/apps/kustomization.yaml` 리소스 = eso·base·hpa |
| core-api 는 SSM 에 키만 넣으면 송출 시작 | `apps-external/externalsecret-runtime.yaml` 허용 목록에 `GRAFANA_*` 포함 |
| daily-api 판정 경로 카운터 없음 | `daily-api/.../support/ClientIpResolver.kt` (core-api 쪽엔 `devquest.ratelimit.client_ip`) |
| daily-api→ai-api AI 호출 지표는 ai-api 레지스트리에만 | `client-ai` `AiMetricsRecorder`·`CacheMetricsAdvisor`, `ai-api` `AiCallLogObservabilityAdapter` |

🟡 **추론 — 구현 때 테스트로 확인**: ① Micrometer 는 `otlp.resourceAttributes` 가 non-null 이면 `OTEL_RESOURCE_ATTRIBUTES` 환경변수를 무시한다(소스 미대조) → 매니페스트만으로 못 고친다. ② Grafana Cloud OTLP 변환은 `service.name→job`, `service.instance.id→instance`(문서 미대조).

## ⑥ 구현 단계 (⑤ 머지 후 `origin/main` 에서 분기)

| 단계 | 내용 | 파일 |
|---|---|---|
| 1 | 리소스 속성: `service.name` 프로퍼티화(기본 `devquest-api` — 기존 시계열 연속) + `service.instance.id`(Fly `FLY_MACHINE_ID` / K8s `HOSTNAME`) + `devquest.platform`(env `DEVQUEST_PLATFORM`). TDD — resourceAttributes 단언 | `OtlpMetricsConfig.kt`·테스트·`monitoring.yml` |
| 2 | Loki 라벨 `platform` 추가 | `logback-spring.xml`·`LogbackLokiConfigTest.kt` |
| 3 | 플랫폼 env | `be/fly.toml [env]`, `k8s/gitops/apps-external/kustomization.yaml` |
| 4 | daily-api 송출 + Loki + 판정 경로 카운터(D-007 복제), 오토컨피그 OTLP 이중 차단 복제(429 이력) | `daily-api/build.gradle.kts`·`application-prod.yml`·Application·`ClientIpResolver.kt` |
| 5 | ai-api 송출(`service.name=devquest-ai-api`) | `ai-api/build.gradle.kts`·yml |
| 6 | 키 배선: daily-api·ai-api 전용 ExternalSecret(optional envFrom) | `k8s/gitops/apps-external/` |
| 7 | 문서: 상위 계획서 B8-18·⑥, 원장 L-78 종결, `secrets-map.md`(Grafana 사본에 daily-api·ai-api), TASK-17 | |

- 비용: **AWS 과금 0**(새 리소스 없음). 검증은 10번 첫 창에 편승. Grafana 무료 티어 활성 시리즈만 관찰 — 파드명 instance 라 창마다 새 시계열, daily-api HPA 최대 4.
- ⑤ 와 겹치는 파일: `apps-external/kustomization.yaml`(🔴 같은 패치 블록) · core-api·daily-api `application-prod.yml`(🟡 forward-headers 인접) · 상위 계획서 표.

## 관측 학습 트랙 (2026-10-30 등록 — 사용자 *"모니터링 역량도 늘리고 싶어"*)

> **착수 조건: 10번 첫 정기 창 이후.** 「10 직전」 순서에 끼워 첫 창을 미루지 않는다.
> 각 단계는 *배우는 개념*이 목적이다 — 도구 이름이 아니라 OTel·PromQL·SLO 처럼 벤더를 옮겨도 남는 것.

| 단계 | 할 일 | 배우는 것 | 완료 판정 |
|---|---|---|---|
| L-1 | (= ⑥ A안) 리소스 속성 분리 | OTel 리소스 모델, 시계열 동일성, 카디널리티 비용 | 첫 창 후 Grafana 에서 `platform` 별 시계열 분리 실측 · 활성 시리즈 수 기록 |
| L-2 | 대시보드 PromQL 정리 — `max_over_time - min_over_time` 우회식을 `increase`/`rate` + `sum by (platform)` 로 | 카운터 의미론(리셋 처리), 집계 연산자 | 대시보드 JSON 갱신 · 창 전환 시각에 가짜 급증 없음 |
| L-3 | 알림·SLO — 창 동안 가용성·지연 SLO, 소진율(burn rate) 알림(Grafana 알림, 무료) | SLI/SLO/에러 예산, 다중 창 burn rate | 알림 규칙 코드화(레포에 정의) · 창 1회 발화/미발화 확인 |
| L-4 | 분산 트레이싱 — Micrometer Tracing → OTLP → Tempo. Worker → core-api/daily-api → ai-api, 로그에 trace id | 컨텍스트 전파(W3C traceparent), 샘플링, 로그↔트레이스 연결 | 한 요청을 Tempo 에서 끝까지 추적 · Loki 로그에서 trace 로 이동 |
| L-5 | (선택) 클러스터 안 수집기(Alloy) — `stage/eks-*` 학습 마일스톤, **prod 경로 아님** | push vs pull(스크레이프), k8s 서비스 디스커버리 | 이해도 퀴즈(`assert-eks-quiz.sh`) · 과금은 그 세션 시간만 |

🔴 L-4 의 Worker 구간 전파와 L-5 는 비용·범위가 커질 수 있다 — 착수 시 단계별로 다시 판정한다.
