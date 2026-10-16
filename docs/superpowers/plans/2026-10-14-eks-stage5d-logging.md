# EKS Stage 5d — 로그 수집 계획서

> 2026-10-14 작성 · 브랜치 `stage/eks-15-logging` · 상태: **계획 (세션 전)**
>
> 5c 퀴즈에서 생긴 단계다. 사용자가 실제 로그를 보며 풀고 싶어 했는데 **남은 로그가 없었다** —
> ArgoCD 컨트롤러 로그와 K8s 이벤트가 노드와 함께 사라졌다(일지 2026-10-14 「Stage 5c 퀴즈」).
> 세션 절차는 `docs/eks-session-sop.md` 를 따른다. 이 문서는 **그 위에 얹는 5d 고유 부분**만 적는다.
> 🟡 표시는 문서·로컬로만 확인했고 실클러스터에서 보지 않은 것이다.

## 1. 무엇을 확인하려는가

한 문장: **파드가 지워지고 클러스터가 destroy 된 뒤에도, 그 파드가 남긴 로그와 그때 일어난 K8s 이벤트를
CloudWatch 에서 찾아 읽을 수 있다는 것을 실클러스터로 확인한다.**

쪼개면:

1. 노드 디스크에만 있던 컨테이너 로그를 **노드마다 하나씩 뜨는 수집기**(DaemonSet)가 읽어 클러스터 밖으로 보낸다.
2. 파일로 남지 않는 **K8s 이벤트**(API 서버 안에 약 1시간 머문다)는 별도 수집기가 API 를 watch 해서 로그로 바꾼다.
3. 로그를 담는 통(로그 그룹)은 **클러스터와 수명이 다른 레이어**(0-bootstrap)에 둔다. 그래야 destroy 가 지우지 않는다.
4. 그 대가로 **영속 리소스가 하나 늘고, 수집량만큼 돈이 든다.** 얼마인지 실측한다.

## 2. 결정 사항

| # | 결정 | 선택 | 근거 |
|---|---|---|---|
| D1 | 저장소 | **CloudWatch Logs** (2026-10-14 사용자 결정) | 기각: S3 원본 파일(검색 화면이 없다) · 학습 전용 Grafana Cloud(외부 계정·자격증명 추가, 분리 규칙 새로 설계) · 클러스터 안 Loki(클러스터와 함께 사라져 목표 미달, 파드 자리 부족) |
| D2 | 🔑 범위 | **파드 로그 + K8s 이벤트.** EKS 컨트롤플레인 로그는 **뺀다** (2026-10-14 사용자 결정) | 목표("파드가 죽어도 남는다")는 이것으로 충족된다. 컨트롤플레인 로그는 `cluster.tf:5` 의 "비용 회피로 생략" 결정을 뒤집는 일이라 결정 블록·비용 모델·클러스터 이름 중복(§5 U-2)까지 함께 고쳐야 한다. §5 「이번에 하지 않는 것」 |
| D3 | 🔑 로그 그룹 위치 | **0-bootstrap** 에 tofu 로 2개: `/devquest/eks-learning/containers` · `/devquest/eks-learning/events` | §5 U-3. 영속 자산은 0-bootstrap 에 둔다(D-004 · S3 백업 · ACM 선례). 1-network 는 영속을 보장하는 레이어가 아니다. 2-cluster 에 두면 destroy 가 지워 목표가 무너진다 |
| D4 | 보존 기간 | **30일** | 세션 뒤 퀴즈·복기가 며칠 안에 일어난다. 30일이면 다음 Stage 퀴즈까지 덮는다. 무기한(`None`)은 금지 — 계정에 이미 무기한 고아 그룹이 하나 있다(`PERSISTENT-RESOURCES.md` `/aws/lambda/test`). 상한은 어차피 계정 수명(2027-01-15, §5 U-9) |
| D5 | 로그 그룹을 언제 만드나 | **세션 전에 0-bootstrap 을 로컬에서 apply** (승인 게이트). 머지 때 CI apply 는 `No changes` 가 된다 | §5 U-1. CI 는 main 머지 때만 0-bootstrap 을 apply 하는데 세션은 브랜치에서 한다. 0-bootstrap 상태는 S3 원격 백엔드라 로컬 apply 와 CI 가 같은 장부를 본다(`0-bootstrap/backend.tf`). 선례: S3 백업 버킷(일지 2026-09-13) |
| D6 | 수집기 | **Fluent Bit 공식 차트** `fluent/fluent-bit` **0.58.3** (앱 5.1.3), 설정을 손으로 쓴다 | 기각: `eks/aws-for-fluent-bit` 0.2.0 — 기본 이미지가 3.2.1(2026-01)로 낡았고, 기본값이 `region: us-east-1` · `autoCreateGroup: true` 라 **빠뜨리면 다른 리전에 무기한 그룹이 생긴다**(`helm show values` 실측). 공식 차트는 입력·필터·출력을 전부 직접 적어야 해서 무엇이 어디로 가는지 파일에 다 보인다. 기각: CloudWatch Observability 애드온(Container Insights 메트릭이 따로 과금) |
| D7 | 🔑 이벤트 수집 | 같은 차트를 **두 번 설치**: ① `fluent-bit` = DaemonSet, 파일 로그만 ② `fluent-bit-events` = **Deployment 1개**, `kubernetes_events` 입력만 | 이벤트는 API 서버에 한 벌뿐이다. DaemonSet 에 이벤트 입력을 넣으면 **노드 수만큼 중복 수집**된다(3대면 3배). 별도 exporter 이미지를 들이지 않고 같은 도구로 끝낸다. 🟡 5.1.3 이미지에 `kubernetes_events` 입력이 있는지는 §9 에서 확인 |
| D8 | 권한 | **IRSA** 역할 1개(`2-cluster/irsa-fluent-bit.tf`), 두 ServiceAccount 를 `sub` 로 허용. 권한은 두 그룹에 대한 `CreateLogStream` · `PutLogEvents` · `DescribeLogStreams` 만 | 레포 패턴(`irsa-eso.tf`). 🔑 **`CreateLogGroup` 을 주지 않는다** — 설정에서 `auto_create_group` 을 빠뜨려도 IAM 이 막아, state 밖 무기한 그룹이 생길 길이 없다(§5 U-1b · U-21). 역할 ARN 은 values 파일에 적지 않고 `--set` 으로 주입(계정 ID, §5 U-5) |
| D9 | 리전 | 출력에 `region ap-northeast-2` **명시** | 차트·플러그인 기본값에 기대지 않는다(D6). 노드의 IMDS 로 알아내게 두지 않는다(§5 U-7 — Karpenter 노드는 hop limit 이 다를 수 있다) |
| D10 | 자기 로그 제외 | tail 입력에서 Fluent Bit 자신의 로그 파일을 뺀다(`Exclude_Path`) | §5 U-21. 수집기가 에러를 내면 그 에러가 다시 수집돼 수집량이 스스로 불어난다 |
| D11 | 로그 형식 | **앱은 건드리지 않는다.** 필터에서 `Merge_Log On` — JSON 줄은 필드로 풀고, 평문 줄은 `log` 필드에 그대로 둔다 | §5 U-6. JSON 은 core-api 뿐이고 ai-api · daily-api 는 평문이다. 차이 자체가 학습 재료다(Logs Insights 에서 필드 질의 대 `parse`) |
| D12 | 설치 방법 | **helm 손 설치**, 네임스페이스 `logging` | §5 U-19. 5c Application 은 `default` 네임스페이스 앱만 맡는다. 클러스터 범위 권한이 필요한 수집기를 넣으면 5c 의 범위 결정이 바뀐다 |
| D13 | 노드 · 기타 | 관리형 **3대**. ArgoCD · Karpenter · metrics-server **설치 안 함** | §3. 이번 질문에 필요 없는 것은 올리지 않는다. 앱 3종은 기존 `sed \| kubectl apply` 로 |
| D14 | 세션 종료의 승인 대기 | 5c D15 그대로 — **사용자가 지켜보는 시간에만 진행**, 측정이 일찍 끝나면 그 자리에서 알린다 | 5c 에서 이 방식으로 비용이 예상대로 끝났다($0.18) |

## 3. 용량 산술

실측값(일지 08-31 · 09-08 · 10-08 · 10-12): 노드당 allocatable 1365Mi · pods 11, 노드당 기존 DaemonSet 3개 104Mi.

```
기본 앱 requests   core 480 + daily 512 + ai 320 + postgres 256        = 1568Mi
시스템 Deployment  ebs-csi-controller 232 + coredns 70                 =  302Mi
ESO                차트 2.8.0 기본 requests **없음**(§9 결과)               =    0Mi
fluent-bit-events  64Mi (D7, Deployment 1개)                           =   64Mi
                                                                 합계  ≈ 1934Mi

노드당 DaemonSet  104 + fluent-bit 64 = 168Mi
3대 × (1365 − 168) = 3591Mi  →  남는 양 ≈ 1657Mi
```

파드 자리: 노드당 DaemonSet 4개(aws-node · kube-proxy · ebs-csi-node · fluent-bit) → 노드당 7칸 남음 × 3 = 21칸.
나머지 파드 ≈ coredns 2 · ebs-csi-controller 2 · ESO 3 · postgres 1 · 앱 3 · events 1 = **12개** → 들어간다.
fluent-bit requests 는 차트 기본이 **없음**이라(`resources: {}`) values 에 직접 준다 — 안 주면 스케줄러가 0 으로 계산한다(5b U-3 함정).

## 4. 통과 기준

| # | 무엇을 | 어떻게 보나 | 통과 | 실패 |
|---|---|---|---|---|
| P1 | 수집기가 떴다 | `kubectl -n logging get pods -o wide` · `kubectl top pod -n logging` | DaemonSet 3/3, events 1/1 `Running`. **메모리 실측 기록** | Pending / CrashLoop / IRSA 오류(`AccessDenied`) |
| P2 | 🔑 앱 로그가 CloudWatch 에 온다 | Logs Insights — containers 그룹, 최근 5분 | 세 앱의 로그가 보이고, 각 줄에 네임스페이스 · 파드 · 컨테이너 이름이 붙어 있다. core-api 는 JSON 필드로 질의된다 | 0건 / 메타데이터 없음 |
| P3 | 🔑 **지운 파드의 로그가 남는다** | `kubectl delete pod <core-api 파드>` → 새 파드 뜬 뒤, **지운 파드 이름**으로 질의 | 지운 파드의 마지막 로그(종료 로그 포함)가 나온다. `kubectl logs <지운 파드>` 는 실패하는 것을 함께 기록 | 지운 파드 로그가 없다 |
| P4 | 🔑 **이벤트가 남는다** | 일부러 실패하는 파드: 없는 이미지 태그로 `kubectl run` → events 그룹 질의 | `Failed` · `BackOff`(`ImagePullBackOff`) 이벤트가 나온다. 같은 파드의 **앱 로그는 0건**인 것도 기록(5c 퀴즈 Q3 의 🟡 를 실측으로) | 이벤트 없음 / 3배 중복(D7 실패) |
| P5 | 🔑 **destroy 뒤에도 읽힌다** | destroy 완료 후 Logs Insights 로 P3 · P4 질의 재실행 | 같은 결과가 나온다. 이 Stage 의 존재 이유 | 결과가 비었다 |
| P6 | 수집량 · 비용 | 세션 끝에 그룹별 `IncomingBytes`(CloudWatch 지표) | 바이트를 기록하고 × $0.76/GB 로 금액 산출. **서비스별 비중**(DEBUG 로그가 얼마인지, §5 U-15) | — |
| P7 | 뒷정리 · 고아 | SOP 종료 절차 + 로그 그룹 검사(§6) | `tofu state list` 0건 · 고아 0. 로그 그룹은 **D3 의 2개만**, 둘 다 보존 30일. `/aws/eks/…` · `/aws/containerinsights/…` · 차트 기본 이름의 그룹 **0개** | 예상 밖 그룹이 있다 |

## 5. 계획과 실제 코드 사이의 불일치 (착수 전 조사)

Blindspot 21건(2026-10-14). 처리 방식만 적는다.

| ID | 발견 | 처리 |
|---|---|---|
| U-1 | 0-bootstrap 에 둔 로그 그룹은 세션 중에 아직 없다(CI 는 머지 때만 apply). 없으면 IRSA 정책 참조 실패 또는 수집기·EKS 가 state 밖 무기한 그룹을 만든다 | D5 (세션 전 로컬 apply) · D8 (`CreateLogGroup` 미부여) |
| U-2 | `cluster_name` 이 1-network · 2-cluster 에 중복, 0-bootstrap 에는 없다 | D2 로 컨트롤플레인 로그를 빼서 **이번엔 해당 없음**. 「이번에 하지 않는 것」 |
| U-3 | 1-network 는 영속 레이어가 아니다 | D3 |
| U-4 | 1노드로는 불가능, 3노드 전제. 메모리 여유를 수집기가 깎는다. 차트 requests 기본 없음 | D13 · §3 |
| U-5 | IRSA: SA 이름 글자 단위 일치 · IAM description ASCII 만 · 역할 ARN 을 values 에 적지 않는다 | D8. 이름은 locals 한 곳에서 정하고 helm `--set` 에도 같은 값 |
| U-6 | JSON 은 core-api 뿐 | D11 |
| U-7 | 노드는 IGW 직결 → VPC 엔드포인트 불필요. Karpenter 노드 IMDS 는 따로 | D9. Karpenter 는 이번에 안 올린다(D13) |
| U-8 | tfsec 하드 페일: KMS 없는 로그 그룹 · 로그 스트림 와일드카드 | 근거 주석을 단 `#tfsec:ignore`(선례 `ecr.tf` · `backend-state.tf`). §9 에서 로컬 tfsec 로 먼저 확인 |
| U-9 | 보존 상한은 retention 이 아니라 계정 수명(2027-01-15) | D4. 원장 행에 재검토일로 적는다 |
| U-10 | 원장 §확인 명령이 "로그 그룹 + Lambda 0 = 고아"로 판정 → 새 그룹을 고아로 오판 | `PERSISTENT-RESOURCES.md` 에 행 추가 + 판정식 수정. 합계 재검산. **실패 6종 표는 건드리지 않는다** |
| U-11 | SOP §9 고아 검사가 로그 그룹을 안 본다 | SOP §9 · §9b 에 로그 그룹 검사 추가(P7) |
| U-12 | 비용 모델 문서가 로그 저장소를 Grafana Cloud 로만 적었다 | `eks-cost-model.md` 의 해당 표에 CloudWatch(학습 클러스터) 추가. 컨트롤플레인 로깅 OFF 문장(:166 · :201)은 **여전히 참** — 그대로 |
| U-13 | 리퍼는 로그 그룹을 모른다. 고치면 하네스 동결 | **고치지 않는다.** 그룹이 0-bootstrap 에 있어 리퍼 경로와 무관하다. 자동 생성 그룹은 D8 이 IAM 으로 막는다 |
| U-14 | 학습 클러스터에 `GRAFANA_*` 를 넣지 않아 Loki 어펜더가 꺼져 있다 — 유지해야 한다 | 그대로. 수집 경로는 stdout → Fluent Bit 하나뿐 |
| U-15 | ai-api 에 prod 프로필이 없어 DEBUG 가 남고, core-api 도 Spring AI DEBUG — 수집량을 키운다 | 이번엔 **측정만**(P6). 줄이는 것은 `be/` 변경이라 범위 밖 |
| U-16 | 로그 그룹 ARN · 질의 결과에 계정 ID 가 들어간다. 0-bootstrap plan 이 공개 Actions 로그를 탄다 | 일지·튜토리얼에 옮길 때 계정 ID 를 지운다. output 은 이름만(ARN 은 2-cluster 에서 조립) |
| U-17 | 컨트롤플레인 로그를 하드코딩하면 매 세션 과금 | D2 로 해당 없음 |
| U-18 | NetworkPolicy 가 수집기를 막지 않는다 | 확인만 |
| U-19 | ArgoCD 로 설치하면 5c 범위가 바뀐다 | D12 |
| U-20 | 5c 계획서가 적어둔 선행 조건 둘(원장 · 파드 자리) | U-10 · U-4 로 반영 |
| U-21 | 수집기 자기 로그 루프, `auto_create_group` 기본값 | D10 · D8 |

### 이번에 하지 않는 것 (다음 작업으로 남긴다)

| 항목 | 왜 지금 안 하나 |
|---|---|
| **EKS 컨트롤플레인 로그**(api · audit · authenticator 등) | 2026-10-14 사용자 결정(D2). 하려면: `cluster.tf:5` 결정을 뒤집는 결정 블록(D-016, 감시 문서 안) · `eks-cost-model.md:166·201` 수정 · `/aws/eks/<cluster>/cluster` 그룹을 미리 만들기 위한 `cluster_name` 단일화(U-2) · 변수 토글 기본 꺼짐(U-17, `db_mode` 선례). audit 양은 미측정<br>→ ✅ **Stage 5f 에서 처리(2026-10-16)** — `2026-10-15-eks-stage5f-control-plane-logs.md`. 차이 2가지: ① 원 결정이 📌 블록이 아니라 `cluster.tf` 주석뿐이라 D-016 대신 **그 주석에 취소선 + 반전 표시** ② `cluster_name` 단일화 대신 **plan 단계 precondition**. audit 실측 98.0% · ≈ $0.067/h |
| 앱 로그 레벨 정리(U-15) · ai-api · daily-api JSON 로깅(U-6) | `be/` 변경 — EKS 학습 Stage 범위가 아니다. P6 실측이 근거 자료가 된다 |
| 리퍼가 로그 그룹을 검사(U-13) | 하네스 동결. D8 이 같은 위험을 IAM 으로 막는다 |
| 지표 필터 · 알람(에러 줄 수로 알림) | 수집이 먼저다. 알람은 SNS · 비용이 따로 붙는다 |
| 수집기를 ArgoCD 로 관리(U-19) | 5c 범위 결정. 시크릿 단계(B)에서 Application 범위를 다시 정할 때 함께 |

## 6. 세션 절차 (5d 고유 부분)

SOP 의 시작·종료 체크리스트는 그대로 따른다. 🔴 **과금 구간에서는 질문하지 않는다.**

```
0. (세션 전, $0) 0-bootstrap 로컬 apply — 로그 그룹 2개 생성        ← 승인 게이트, 과금 없음(빈 그룹)
1. 2-cluster apply (-var node_desired_size=3)                       ← 과금 시작
2. 손으로 올린다: ESO → 시크릿 → postgres → 비밀번호 동기화 → 앱 3종
3. fluent-bit (DaemonSet) · fluent-bit-events (Deployment) 설치     → P1
4. Logs Insights 질의                                              → P2
5. core-api 파드 삭제 → 지운 파드 이름으로 질의                       → P3
6. 없는 이미지로 kubectl run → events 그룹 질의                      → P4
7. 그룹별 IncomingBytes 기록                                        → P6
8. destroy                                                         → P7
9. destroy 뒤 P3 · P4 질의 재실행                                   → P5
```

미리 정해 둔 분기:

| 상황 | 행동 |
|---|---|
| 수집기가 `AccessDenied` | SA 이름 · 네임스페이스와 IRSA `sub` 를 글자 단위로 대조(U-5). 10분 안에 못 풀면 P2~P4 를 포기하고 원인만 기록 |
| `kubernetes_events` 입력이 안 뜬다 | P4 를 `kubectl get events -A -o json` 파일 저장으로 대체하고 기록(5c §5 의 최소 조치) |
| 수집량이 세션 중 **300MB 를 넘는다** | 수집기 두 개를 즉시 `helm uninstall` 하고 원인(어느 파드가 쏟는지)만 기록 |
| 60분 경과 | 남은 항목을 버리고 7 → 8 로 간다 |

## 7. 비용

| 구간 | 시간 | 단가 | 금액 |
|---|---|---|---|
| 0. 0-bootstrap apply | — | 빈 로그 그룹 $0 | $0 |
| apply · 작업 · destroy | 약 60분 | 5c 와 같은 구성(3대) | $0.18 |
| 로그 수집 | 세션 1회 | $0.76/GB · 🟡 양 미측정 — 100MB 면 $0.08 | ≤ $0.08 |
| 저장 · 질의 | 30일 | $0.0314/GB-월 · $0.0076/GB 스캔 | ~$0 |
| **예상** | | | **$0.26** |
| **상한** | 90분 + 300MB | | **$0.53** |

단가는 2026-10-14 `aws pricing get-products`(서울) 실측(일지 같은 날짜).

## 8. 만들 파일

| 파일 | 내용 |
|---|---|
| `infra/aws-eks/0-bootstrap/log-groups.tf` | D3 · D4 — 그룹 2개, 보존 30일, tfsec 근거 주석 |
| `infra/aws-eks/0-bootstrap/outputs.tf` | 그룹 **이름** output(ARN 아님, U-16) |
| `infra/aws-eks/2-cluster/irsa-fluent-bit.tf` | D8 — 역할 · 정책 · output(역할 ARN, `--set` 주입용) |
| `k8s/logging/fluent-bit.yaml` | D6 · D9 · D10 · D11 — DaemonSet 용 values(설정 전문) |
| `k8s/logging/fluent-bit-events.yaml` | D7 — Deployment 1개 · `rbac.eventsAccess: true` |
| `infra/aws-eks/PERSISTENT-RESOURCES.md` | U-10 — 행 추가 · 판정식 · 합계 |
| `docs/eks-session-sop.md` §9 · §9b | U-11 — 로그 그룹 검사 |
| `docs/eks-cost-model.md` | U-12 — 로그 저장소 표 |
| `docs/eks-tutorial-steps.md` Stage 5d 절 | 세션 뒤 |

## 9. $0 준비에서 확인할 것

- `cr.fluentbit.io/fluent/fluent-bit:5.1.3` 이 **arm64** 이미지를 갖고 있는가(노드가 t4g). `kubernetes_events` 입력 · `cloudwatch_logs` 출력이 들어 있는가(`fluent-bit -h` 의 플러그인 목록).
- `helm template` 두 벌: DaemonSet 1개 · Deployment 1개, requests 가 들어갔는가, events 쪽 ClusterRole 에 `events` `list/watch` 가 있는가, DaemonSet 쪽 설정에 `kubernetes_events` 가 **없는가**.
- 렌더된 설정에 `region ap-northeast-2` · `auto_create_group Off` · 자기 로그 `Exclude_Path` 가 있는가.
- `tofu plan`(0-bootstrap) 이 **로그 그룹 2개 add 만** 내는가. 로컬 `tfsec` 이 통과하는가(U-8).
- `tofu validate`(2-cluster) — IRSA `sub` 두 개가 helm 의 SA 이름과 글자 단위로 같은가.
- ESO 의 requests(§3 의 빈칸).

### 결과 (2026-10-14, 전부 로컬 · $0)

| 확인 | 결과 |
|---|---|
| 이미지 | `docker manifest inspect cr.fluentbit.io/fluent/fluent-bit:5.1.3` → `amd64` · `arm` · **`arm64`** ✅. arm64 로 띄운 `-h` 에 `kubernetes_events` · `tail` · `kubernetes` · `cloudwatch_logs` 4개 모두 있음 ✅ (`Fluent Bit v5.1.3`) |
| 플러그인 옵션 | `cloudwatch_logs` 에 `region` · `auto_create_group` · `log_stream_template` 존재, `kubernetes_events` 에 `kube_retention_time` 존재 — 설정에 쓴 키가 전부 실재 ✅ |
| `helm template` DaemonSet 쪽 | DaemonSet 1 · ClusterRole(이벤트 권한 없음) · requests `50m/64Mi` ✅. 설정에 `kubernetes_events` **0건** ✅, `region ap-northeast-2` · `auto_create_group Off` · `Exclude_Path` ✅, 차트 기본의 `elasticsearch` · `systemd` 0건 ✅ |
| `helm template` 이벤트 쪽 | Deployment 1 · ClusterRole 에 `events` `get/list/watch` ✅ · hostPath 볼륨 **없음**(설정 ConfigMap 만) ✅ · requests `20m/64Mi` |
| `Exclude_Path` | `fluent-bit*.log` 는 이벤트 수집기 파드(`fluent-bit-events-…`)의 로그도 함께 뺀다 — 같은 루프 위험이라 의도와 맞다 |
| ESO requests | 차트 2.8.0 기본값에 `resources` 없음 → 0Mi (§3 반영) |
| tfsec | 0 문제 ✅. **실패 주입**: 사본에서 ignore 2줄을 지우면 정확히 `2 potential problem(s)` — ignore 가 실제로 그 둘을 덮는다 ✅ |
| `tofu validate` (2-cluster) | `Success! The configuration is valid.` ✅ |
| `tofu plan` (0-bootstrap) | `Plan: 2 to add, 0 to change, 0 to destroy.` — 로그 그룹 2개(`retention_in_days = 30`)만 ✅ |
| 0-bootstrap apply | 사용자 승인 후 저장한 계획 그대로 apply. 그룹 2개 `retentionInDays 30` · `storedBytes 0` ✅. 계정 전체 그룹 수 3 (= 학습용 2 + 기존 고아 1) ✅ |
| `tofu plan` (2-cluster) | `Plan: 45 to add` (클러스터 전체 — 이번 추가분은 3). 쓰기 정책 문서가 plan 시점에 풀림 = 원격 상태 참조 정상 ✅. Action 3개 · Resource 는 두 그룹 `:*` 만 · `CreateLogGroup` 없음 ✅. 신뢰 `sub` 2개가 values 의 SA 이름과 일치 ✅ |
| 🟡 남은 것 | IRSA 가 실제로 붙는지는 세션에서(P1) |

### 1차 세션 (2026-10-14 17:20~17:42, 중단)

apply 후 kubectl·destroy 가 auto mode 분류기에 거부돼 검증 0건으로 destroy. 원인은 `tofu -chdir=…` 형태가 settings `ask` 와 세션 마커 훅을 둘 다 비껴간 것 — 일지 2026-10-14 · SOP §5. 재시도 시 `cd infra/aws-eks/2-cluster && tofu apply …` 형태로만 친다. kubectl 이 분류기에 다시 막히면 즉시 destroy(같은 형태).

### 2차 세션 결과 (2026-10-15 09:43~10:08, 성공)

| 기준 | 결과 |
|---|---|
| P1 수집기 동작 | ✅ DaemonSet 3 + Deployment 1 Running, IRSA 로 스트림 생성, 에러 0 |
| P2 앱 로그 | ✅ core 262 · daily 201 · ai 140 (Insights). core-api JSON 이 `level`·`message` 필드로 풀림 |
| P3 지운 파드 | ✅ `kubectl logs` NotFound, CloudWatch 268건 |
| P4 이벤트 | ✅ events 18건(ErrImagePull·ImagePullBackOff), containers 0건 |
| P5 destroy 뒤 | ✅ 268건 · 20건 그대로 |
| P6 수집량 | 1.10MB + 0.38MB ≈ $0.001 |
| P7 정리 | ✅ 과금 리소스 0, 로그 그룹 2 + 기존 고아 1 |

비용: 2차 ≈ $0.08, 1차 포함 ≈ $0.15. 계획과 달리 수집기를 앱보다 먼저 깔았다(기동 로그 확보 — `Read_from_Head` 기본 Off).

#### 이번에 하지 않는 것 (세션에서 새로 나온 것)

| 항목 | 왜 지금 안 하나 |
|---|---|
| 수집기 requests 64Mi → 실측 6~7Mi 에 맞추기 | 노드 자리가 남아 손해가 없다. 다음에 values 를 만질 때 함께 |
| tail `Refresh_Interval` 60s(새 파드 ~65초 지연) · `Read_from_Head` Off | 단명 파드 로그 유실 가능성. 지금 앱은 오래 살아 영향 없음. 배치 Job 을 올릴 때 재검토 |
| 세션 마커 훅이 상대경로라 작업 폴더가 바뀌면 안 돈다 | 하네스 동결. SOP §5 의 "apply 직후 마커 육안 확인"으로 막았다(실제로 막았다) |

