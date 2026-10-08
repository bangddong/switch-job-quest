# EKS Stage 5a — metrics-server + HPA 계획서

> 2026-10-07 작성 · 브랜치 `stage/eks-12-hpa` · 상태: **실행 완료 (2026-10-08)**
>
> **결과**: P1·P2·P3·P4·P6·P7 통과, **P5 조건부**(연결 재사용 시 새 파드가 80초간 유휴 — U-3 실증). E1·E2 관측.
> 계획에 없던 발견 1건(콜드 JVM 이 HPA 를 과확장). 🔴 **비용 상한 초과: 예상 $0.23 · 상한 $0.32 → 약 $0.59.**
> §6 의 *"90분이면 무조건 끈다"* 는 지켜지지 않았다 — `destroy` 호출이 승인 대기로 멈춘 동안 그 규칙을 실행할
> 주체도 멈춰 있었다. 실측 원문과 원인은 `docs/eks-migration-log.md` 2026-10-08 엔트리.
> Stage 5 를 5a(지표·HPA) → 5b(Karpenter) → 5c(ArgoCD) 로 나눈 첫 번째다.
> 세션 절차는 `docs/eks-session-sop.md` 를 따른다. 이 문서는 **그 위에 얹는 5a 고유 부분**만 적는다.

## 1. 무엇을 확인하려는가

한 문장: **부하가 오르면 `daily-api` 파드 수가 지표를 근거로 늘고, 부하가 빠지면 줄어든다는 것을
실클러스터에서 숫자로 확인한다.**

지금 클러스터에는 지표를 모으는 장치가 없다. `kubectl top` 이 동작하지 않아 09-08 부하 실측 때는
컨테이너 cgroup 파일을 직접 읽었다(`k8s/loadtest/measure-memory.sh`). 앱 3종은 전부 `replicas: 1`
고정이다.

| 세울 것 | 하는 일 | 없으면 |
|---|---|---|
| **metrics-server** | 각 노드의 kubelet 에서 파드별 CPU·메모리 사용량을 모아 `metrics.k8s.io` API 로 내놓는다 | `kubectl top` 불가. HPA 가 볼 숫자가 없어 `<unknown>` 으로 멈춘다 |
| **HPA** (HorizontalPodAutoscaler) | 15초마다 그 숫자를 읽어 `필요 파드 수 = 올림(현재 파드 수 × 현재값 ÷ 목표값)` 으로 Deployment 의 `replicas` 를 고친다 | 사람이 `kubectl scale` 을 쳐야 한다 |

## 2. 결정 사항

| # | 결정 | 선택 | 근거 · 기각한 대안 |
|:-:|---|---|---|
| D1 | metrics-server 설치 방법 | **EKS 애드온** (`aws_eks_addon`, `2-cluster/addons.tf`) | 1.36 용 `v0.9.0-eksbuild.11` 이 카탈로그에 있다(2026-10-07 조회). `tofu destroy` 가 함께 지우므로 고아가 안 남는다. ⚠️ 레포 관례는 *"AWS 1st-party 만 애드온, 서드파티는 CLI helm"* 인데 이 애드온은 `owner: community · publisher: eks` 라 경계선이다. 기각: helm (설치·삭제 절차가 한 벌 더 생긴다) |
| D2 | 스케일 대상 | **`daily-api` 하나** | 부하 스크립트 요청 3개 중 2개가 `daily-api` 로 간다. 🔴 `core-api` 는 `@Scheduled` 잡 2개(`DailyMailScheduler`·`RateLimitResetScheduler`)를 갖고 있고 분산 락이 없어, 복제하면 잡이 파드 수만큼 중복 실행된다. `daily-api` 에는 `@Scheduled` 가 없다 |
| D3 | 지표와 목표값 | **CPU 사용률 70%** (requests 200m 대비 = 140m) | CPU requests 가 이미 선언돼 있어 추가 장치가 필요 없다. 기각: 메모리 (SerialGC 가 OS 에 반환하지 않아 한 번 오르면 안 내려온다 = 09-08 실측. 축소가 영영 안 일어난다) · RPS (커스텀 지표 어댑터가 따로 필요, 5a 범위 밖) |
| D4 | 노드 수 | **4대** (`-var node_desired_size=4`, `node_max_size` 3 → 4) | 아래 §3 산술. 3대로는 부하 생성기를 격리하면 복제본이 **한 개도 더 안 들어간다** |
| D5 | 복제 범위 | `minReplicas 1` · `maxReplicas 4` | 4대에서 3개까지는 최악 배치에서도 들어간다(§3). 4번째는 배치에 따라 갈리며, **안 들어가면 그것이 5b 의 출발 장면**이다 |
| D6 | 축소 대기 시간 | `scaleDown.stabilizationWindowSeconds: 60` | 기본값 300초는 과금 5분이다. 기본값이 300초인 이유(들쭉날쭉한 부하에서 늘렸다 줄였다 반복 방지)는 튜토리얼에 설명으로 남긴다 |
| D7 | HPA 매니페스트 위치 | `k8s/hpa/daily-api-hpa.yaml` (신규 디렉토리) | `k8s/base/` 에 넣으면 HPA 를 쓰지 않는 다른 세션에도 적용된다. `k8s/eso/`·`k8s/loadtest/` 와 같은 분리 방식 |
| D8 | 외부 노출 | **ALB 없음** | 부하는 클러스터 안 k6 가 Service 로 건다. ALB 는 시간당 $0.0325 에 LCU 가 따로 붙고, 튜토리얼이 *"부하 테스트와 Stage 4 를 같은 세션에 넣지 마라"* 고 적어 뒀다 |

## 3. 용량 산술 — 왜 4대인가

전부 기존 실측값이다(일지 08-31·09-08): 노드당 allocatable **1365Mi**, 노드당 DaemonSet **104Mi**,
`ebs-csi-controller` 232Mi, `coredns` 70Mi.

```
기본 앱 requests   core 480 + daily 512 + ai 320 + postgres 256        = 1568Mi
시스템 Deployment  ebs-csi-controller 232 + coredns 70                 =  302Mi
metrics-server     🟡 200Mi (upstream 기본값. 애드온 실제값은 세션에서 측정)

부하 생성기 k6 는 노드 하나를 비워 거기에만 둔다(09-08 에 확립 — 같은 노드면 측정이 오염된다)
⇒ 앱이 쓸 수 있는 노드 = 전체 − 1

3대: 앱 노드 2대  2 × (1365−104) = 2522 − 302 − 200 − 1568 =  452Mi   ← daily-api 512 가 안 들어간다
4대: 앱 노드 3대  3 × (1365−104) = 3783 − 302 − 200 − 1568 = 1713Mi   ← 총량으로 3개
```

조각화를 따진 최악 배치(4대):

```
A  core 480 + daily 512                                  = 992   남음 269
B  ai 320 + postgres 256 + csi 232 + coredns 70 + ms 200 = 1078  남음 183
C  비어 있음                                               남음 1261  → daily 2개(1024)
⇒ 복제본 3개(1+2)는 최악에서도 성립. 4번째는 배치 의존.
```

vCPU 쿼터는 32(2026-10-07 조회)이고 4대는 8 vCPU 다.

## 4. 통과 기준

`✅` 는 README 정의대로 *"실클러스터에서 확인했다"* 다. 각 항목은 **틀렸을 때 다른 값이 나오는**
검사로 잡는다.

| # | 주장 | 검사 | 통과 | 실패하면 보이는 것 |
|:-:|---|---|---|---|
| P1 | 지표가 수집된다 | `kubectl get apiservice v1beta1.metrics.k8s.io` · `kubectl top pods` | `Available=True` + 파드별 숫자 | `ServiceUnavailable` / `metrics not available yet` |
| P2 | 그 숫자가 실제 사용량이다 | 같은 구간에서 `kubectl top` 의 CPU 와 cgroup `cpu.stat` `usage_usec` 델타를 비교 | 차이 ±20% 이내 | 숫자는 나오는데 엉뚱한 값 (P1 만으로는 못 잡는다) |
| P3 | HPA 가 지표를 읽는다 | `kubectl get hpa` 의 TARGETS | `n%/70%` | `<unknown>/70%` |
| P4 | 부하에 늘어난다 | 부하 중 `kubectl get hpa -w` | REPLICAS 1 → 2 이상. 임계 초과 시각 → 새 파드 Ready 시각을 기록 | 1 에서 안 움직임 |
| P5 | 🔑 **늘어난 파드가 실제로 일을 한다** | 확장 후 파드별 `kubectl top` | 모든 복제본의 CPU 가 유휴보다 뚜렷이 높다 | 첫 파드만 높고 나머지는 유휴 (§5 U-3) |
| P6 | 부하가 빠지면 줄어든다 | 부하 중지 후 관찰 | REPLICAS → 1, 60초 창 이후 | 그대로 남음 |
| P7 | 뒷정리 | SOP 종료 절차 | `tofu state list` 0건, 고아 0 | — |

덤으로 볼 것(통과 기준은 아니다):

- **E1 — `replicas` 충돌**: HPA 가 3으로 늘린 상태에서 `kubectl apply -f k8s/base/daily-api.yaml` 을 다시
  치면 매니페스트의 `replicas: 1` 이 덮어쓰는가, HPA 가 얼마 만에 되돌리는가. 1분짜리 실험.
- **E2 — 천장**: 부하를 더 올려 HPA 가 4개를 원할 때 4번째가 뜨는가, `Pending / Insufficient memory` 인가.
  Pending 이면 그 이벤트 원문을 일지에 남긴다(5b 의 출발점).

## 5. 계획과 실제 코드 사이의 불일치 (착수 전 조사)

| # | 계획이 가정한 것 | 실제 | 이번에 |
|:-:|---|---|---|
| U-1 | 지난 부하 실측에 CPU 사용량이 있어 목표값을 미리 정할 수 있다 | 🔴 **없다.** 09-08 일지에는 *"스로틀 전 컨테이너 0"* 뿐이고 스테이지별 CPU 사용량이 기록되지 않았다. 게다가 CPU limits 가 없어 스로틀은 원래 0일 수밖에 없다 | 세션 첫 측정으로 채운다. 목표값 70% 는 고정하고 **부하 단계를 올려 맞춘다**(§6 규칙) |
| U-2 | 앱 아무거나 복제하면 된다 | `core-api` 는 스케줄 잡 2개가 중복 실행된다. 메일 잡은 09:00 KST 에 돈다 | 대상에서 제외(D2) |
| U-3 | 파드를 늘리면 부하가 나뉜다 | 🟡 **안 나뉠 수 있다.** Service(ClusterIP)는 요청 단위가 아니라 **연결 단위**로 파드를 고른다. k6 는 연결을 재사용하므로 이미 맺은 연결은 계속 첫 파드로 간다 | P5 로 직접 확인. 안 나뉘면 `--no-connection-reuse` 로 다시 돌려 비교한다 |
| U-4 | 새 파드가 뜨면 곧바로 평균에 들어간다 | Spring 기동이 2~3분이고 기동 중 CPU 가 튄다. HPA 는 Ready 전 파드를 계산에서 뺀다. EKS 는 컨트롤플레인 플래그를 바꿀 수 없다 | 관찰만 한다. 임계 초과 → Ready 까지의 시간을 기록 |
| U-5 | `daily-api` 는 롤링 업데이트다 | `strategy: Recreate` 다(파드 슬롯 11칸 때문에 넣은 것) | HPA 확장과는 무관. 건드리지 않는다 |
| U-6 | 매니페스트의 `replicas: 1` 은 무해하다 | HPA 가 관리하는 필드를 `kubectl apply` 가 덮어쓴다 | 지우지 않고 E1 실험으로 실물을 본다 |
| U-7 | 3대면 충분하다 | §3 — 한 개도 더 안 들어간다 | 4대(D4) |
| U-8 | 복제해도 DB 는 괜찮다 | `daily-api` prod 프로파일 풀 10 × 4 = 40, `core-api` 10. postgres 기본 `max_connections` 100 | 범위 안. 숫자만 기록 |

### 이번에 하지 않는 것 (다음 작업으로 남긴다)

| 항목 | 왜 지금 안 하나 |
|---|---|
| `core-api` 를 복제 가능하게 만들기 (스케줄 잡에 분산 락) | 제품 코드 변경이고 5a 의 목표가 아니다. 상시 운영을 하게 되면 필수 |
| `RateLimitResetScheduler` 의 인메모리 버킷 (복제하면 1인 한도가 파드 수만큼 늘어난다) | 위와 같음. 계획서 `2026-09-27-prod-eks-timeboxed-migration.md` U-6 이 이미 적어 둔 사실 |
| 매니페스트에서 `replicas` 필드 제거 | HPA 를 상시 쓰기로 정하면 그때. 지금은 HPA 가 세션 한정 |
| CPU limits 도입 여부 | 5a 는 limits 없이 본다. 넣으면 스로틀이라는 변수가 하나 더 생긴다 |
| **세션 종료가 사람의 승인에 걸려 있다** (2026-10-08 세션에서 발견) | 🔴 다음 유료 세션(5b) **전에** 정해야 한다. 선택지: `tofu destroy` 사전 허용 규칙 / 끌 때까지 자리 확인 / 세션 중 수면 방지. 사용자 결정 대기 |
| 콜드 JVM 과확장 완화 (`behavior.scaleUp` 정책, 기동 CPU 부스트 등) | 5a 는 현상 확인까지. 상시 운영을 하게 되면 다룬다 |
| 부하 스크립트의 `explain` 요청이 429 (전체의 1/3) | 과금 중이라 진단을 멈췄다. 09-08 실측의 req/s 가 같은 조건이었는지 미확인 |
| RPS·지연 기반 스케일 (커스텀 지표) | Prometheus 어댑터가 필요. 5a 범위 밖 |
| `PodDisruptionBudget`·`topologySpreadConstraints` | 노드가 단일 AZ 라 지금은 보여 줄 장면이 없다 |

## 6. 세션 절차 (5a 고유 부분)

SOP 의 시작·종료 체크리스트는 그대로 따른다. 🔴 **과금 구간에서는 질문하지 않는다.** 아래 분기는
그래서 전부 미리 정해 둔다.

```
[$0]  코드 준비 · tofu validate/plan · 매니페스트 dry-run · 이 계획서 승인
[과금] ① tofu apply -var node_desired_size=4          (전경, 마커 육안 확인)
      ② 기본 앱 기동 · postgres 비밀번호 동기화(SOP 6b)
      ③ P1 · metrics-server 실제 requests 기록
      ④ k6 노드 격리(pin-loadgen-node.sh) · 워밍업 1회
      ⑤ 기준선: RATE=25 로 1분 → P2(top vs cgroup) · daily-api CPU 기록
      ⑥ HPA 적용 → P3
      ⑦ 부하 → P4 · P5
      ⑧ 부하 중지 → P6
      ⑨ E1 · E2
      ⑩ SOP 종료 절차(전경 destroy · 완료 게이트) → P7
```

미리 정한 분기:

| 상황 | 행동 |
|---|---|
| ⑤에서 RATE=25 의 CPU 가 이미 140m(70%)를 넘는다 | 그대로 ⑦을 RATE=25 로 진행 |
| ⑦에서 RATE 를 25 → 50 → 100 → 200 → 400 까지 올려도 70% 를 못 넘는다 | 목표값을 바꾸지 않는다. 최고 단계의 사용률을 기록하고 P4 를 **미통과**로 적은 뒤 ⑩으로 간다 |
| P5 에서 첫 파드만 일한다 | `--no-connection-reuse` 로 ⑦을 한 번 더. 두 결과를 나란히 기록 |
| P1 이 5분 안에 안 된다 | 에러 원문을 기록하고 ⑩. 과금 중 원인 분석을 하지 않는다 |
| 과금 시작 후 **90분** | 어디까지 했든 ⑩ |
| 노드 4대가 안 뜬다(용량 부족 등) | 3대로 재시도하지 않는다(§3 에서 불가). ⑩ |

## 7. 비용

| | 시간당 |
|---|---:|
| 컨트롤플레인 | $0.1000 |
| 노드 4대 (`t4g.small` $0.0208 + 공인 IP $0.005 + 루트 EBS $0.0025) × 4 | $0.1132 |
| **합계** | **$0.2132** |

예상 65분 ≈ **$0.23**, 상한 90분 ≈ **$0.32**. 누적 지출은 $5.95 다(2026-10-07 예산 조회).
⚠️ 노드 인스턴스 요금이 그동안 $0 로 청구돼 온 사실이 있다(일지 10-07, 원인 미확인). 위 금액은
정가 기준이라 실제 청구는 더 적을 수 있다.

## 8. 바꿀 파일

| 파일 | 변경 |
|---|---|
| `infra/aws-eks/2-cluster/addons.tf` | `aws_eks_addon.metrics_server` 추가 (`replicas: 1`) |
| `infra/aws-eks/2-cluster/variables.tf` | `node_max_size` 3 → 4 (기본 `desired` 는 1 그대로라 비용 변화 없음) |
| `k8s/hpa/daily-api-hpa.yaml` | 신규 |
| `docs/eks-tutorial-steps.md` | Stage 5a 절 (세션에서 동작 확인한 명령만) |
| `docs/eks-migration-log.md` | 세션 중 실시간 |
| `infra/aws-eks/README.md` | 진행 현황 표에 5a 행 (실클러스터 확인 후에만 `✅`) |
| `docs/eks-quizzes/stage-eks-12-hpa.md` | PR 전 이해도 퀴즈 (필수) |

`be/`·`fe/` 는 건드리지 않는다.
