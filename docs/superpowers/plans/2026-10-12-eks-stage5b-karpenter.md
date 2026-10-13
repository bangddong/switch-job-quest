# EKS Stage 5b — Karpenter 계획서

> 2026-10-12 작성 · 브랜치 `stage/eks-13-karpenter` · 상태: **실행 완료 (2026-10-12)**
>
> **결과**: P1~P6·P8 통과, **P7 조건부**(인스턴스 프로파일 정리는 실물 확인, EC2 종료 경로는 Karpenter 가 먼저 지워 미확인).
> 계획에 없던 발견 2건: ① 부하 중인 노드를 `Underutilized` 로 지워 HPA 와 맞물려 진동 ② `t4g.medium` 은 이 계정이 띄울 수 없다
> (`not eligible for Free Tier`) — NodePool 후보에서 뺐다. E2 미관측.
> 🔴 **비용 상한 초과: 예상 $0.27 · 상한 $0.40 → 약 $0.53.** 측정은 29분에 끝났고 destroy 승인을 64분 + 89분 기다렸다.
> §6 의 *"apply 전에 시각 확답"* 은 막지 못했다. 원문은 `docs/eks-migration-log.md` 2026-10-12 엔트리.
>
> Stage 5 의 두 번째다(5a 지표·HPA ✅ → **5b Karpenter** → 5c ArgoCD).
> 세션 절차는 `docs/eks-session-sop.md` 를 따른다. 이 문서는 **그 위에 얹는 5b 고유 부분**만 적는다.
> 🟡 표시는 문서·소스로만 확인했고 실클러스터에서 보지 않은 것이다.

## 1. 무엇을 확인하려는가

한 문장: **파드가 자리가 없어 `Pending` 이 되면 노드가 스스로 생기고, 비면 스스로 사라진다는 것을
실클러스터에서 시각과 함께 확인한다.**

5a 는 여기서 끝났다. HPA 가 `daily-api` 를 4개로 늘리려 했는데 4번째가 뜨지 못했다.

```
0/4 nodes are available: 1 node(s) were unschedulable, 3 Insufficient memory.
```

HPA 는 **파드 수**를 고친다. 그 파드를 올릴 **노드**가 모자라면 HPA 는 할 수 있는 일이 없다.
노드 수를 고치는 장치가 따로 필요하고, 그게 Karpenter 다.

| 용어 | 하는 일 | 지금 레포의 대응물 |
|---|---|---|
| **Karpenter** | 클러스터 안에서 도는 컨트롤러. `Pending` 파드를 보고 **EC2 를 직접 띄워** 노드로 붙이고, 빈 노드는 지운다 | 없음. 지금은 `-var node_desired_size=4` 로 사람이 정한다 |
| **NodePool** | "어떤 노드를 얼마까지 만들어도 되는가" 의 규칙. 아키텍처·인스턴스 종류·AZ·총량 상한 | 관리형 노드그룹의 `instance_types`·`scaling_config` |
| **EC2NodeClass** | "그 노드를 AWS 에서 어떻게 만드는가". AMI·서브넷·보안그룹·IAM 역할 | 관리형 노드그룹의 `ami_type`·`subnet_ids`·`node_role_arn` |
| **NodeClaim** | Karpenter 가 노드 한 대를 만들 때마다 남기는 기록. 노드 1 : NodeClaim 1 | 없음 |
| **통합** (consolidation) | 비었거나 덜 찬 노드를 지워 비용을 줄이는 동작 | 없음 |

관리형 노드그룹(지금 방식)과 무엇이 다른가:

| | 관리형 노드그룹 + Cluster Autoscaler | Karpenter |
|---|---|---|
| 노드를 늘리는 방법 | Auto Scaling Group 의 숫자를 올린다 | EC2 API 를 직접 부른다 |
| 인스턴스 종류 | 그룹마다 미리 정해 둔 것 | `Pending` 파드의 요구량을 보고 **그때 고른다** |
| 🔴 **OpenTofu 가 아는가** | 안다 (state 에 있다) | **모른다.** Karpenter 가 만든 EC2 는 state 밖이다 → §5 U-1 |

## 2. 결정 사항

| # | 결정 | 선택 | 근거 · 기각한 대안 |
|:-:|---|---|---|
| D1 | Karpenter 버전 | **1.14.1** (helm, `oci://public.ecr.aws/karpenter/karpenter`) | 호환표상 K8s 1.36 은 **1.13 이상**이 필요하다(2026-10-12 조회). 1.15.0 은 이틀 전(10-10) 나왔다 — 기각. 기각: EKS Auto Mode (노드 관리를 통째로 AWS 에 넘기는 방식이라 Karpenter 의 부품이 보이지 않고, 관리 수수료가 따로 붙는다) |
| D2 | 컨트롤러의 AWS 권한 | **IRSA** (`irsa-karpenter.tf` 신규) | 이 레포의 관례다(ALB·EBS CSI·ESO 가 전부 IRSA, OIDC 공급자가 이미 있다). 기각: Pod Identity (공식 안내의 현재 기본이지만 애드온과 노드당 DaemonSet 이 하나 더 필요하다. 노드당 여유 메모리가 이미 빠듯하다) |
| D3 | Karpenter 노드의 IAM 역할 | **기존 `aws_iam_role.node` 재사용** | 관리형 노드그룹이 이 역할의 access entry 를 이미 만든다 ✅(세션에서 확인 — Karpenter 노드가 21초에 등록됐다) → 추가 리소스 없이 노드가 붙는다. 기각: 전용 역할 (공식 안내 방식. access entry 를 따로 만들어야 하고 실습에서 얻는 것이 없다) |
| D4 | 컨트롤러가 사는 곳 | **관리형 노드그룹** (그대로 유지) | Karpenter 는 자기가 만든 노드 위에서 돌면 안 된다(그 노드를 지우면 자신이 죽는다). 차트 기본값이 `karpenter.sh/nodepool DoesNotExist` 로 이미 막아 둔다(1.14.1 `values.yaml` 확인) |
| D5 | 컨트롤러 크기 | `replicas: 1` · requests `cpu 200m / memory 256Mi` 🟡 | 차트 기본은 `replicas: 2` + 필수 반친화성, requests **없음**이다. 공식 안내는 1 vCPU/1Gi 를 권하는데 `t4g.small` 노드(여유 1261Mi)에 넣을 수 없다. 256Mi 는 추정이라 세션에서 실측한다 |
| D6 | 만들 수 있는 노드 | `arm64` · `on-demand` · ~~`t4g.small`/`t4g.medium`~~ → **`t4g.small` 만**(세션에서 정정, 위 결과 ②) · AZ 는 영속 볼륨과 같은 곳 · **총량 상한 `cpu: 4`** | 상한이 곧 비용 상한이다(§7). 기각: 스팟 (중단 알림 큐·EventBridge 규칙 4개가 따라온다. 별개 주제) |
| D7 | 서브넷·보안그룹 찾는 법 | 서브넷: 기존 태그 `kubernetes.io/cluster/devquest-eks: shared` + NodePool 의 AZ 제한. 보안그룹: 클러스터 SG 의 `kubernetes.io/cluster/devquest-eks: owned` 🟡 | 공식 안내는 `karpenter.sh/discovery` 태그를 새로 붙이는데, 그러려면 이미 적용돼 있는 `1-network` 레이어를 다시 건드려야 한다. 있는 태그로 충분하다. 🟡 SG 태그는 세션에서 적용 전에 조회로 확인(§6 분기) |
| D8 | 노드그룹 크기 | **4대** (5a 와 동일) | §3 산술. 5a 와 같은 출발선이라 *"같은 부하에서 4번째가 이번엔 뜬다"* 를 직접 비교할 수 있다 |
| D9 | 🔴 **뒷정리 보장** | **`tofu destroy` 하나로 Karpenter 가 만든 것까지 지운다** (`terraform_data` 의 삭제 시점 실행기) | §5 U-1. 리퍼는 `tofu destroy` 만 친다 — 사람이 사라진 상황에서 `kubectl delete nodepool` 을 쳐 줄 주체가 없다. 기각: 리퍼 스크립트 수정 (하네스 동결. 그리고 수동 destroy 경로는 여전히 뚫려 있다) · 문서로만 안내 (09-16 에 *"쳤다 ≠ 끝났다"* 로 15.8시간 샜다) |
| D10 | 빈 노드 회수 대기 | `consolidationPolicy: WhenEmptyOrUnderutilized` · `consolidateAfter: 30s` | 기다리는 시간이 과금이다. 5a 의 `stabilizationWindowSeconds: 60` 과 같은 이유 |
| D11 | 매니페스트 위치 | `k8s/karpenter/` (신규) | `k8s/hpa/` 와 같은 분리 방식. base 에 넣으면 Karpenter 없는 세션에서 CRD 가 없어 apply 가 실패한다 |
| D12 | 외부 노출 | **ALB 없음** | 5a D8 과 같다 |

## 3. 용량 산술 — 4번째 복제본은 반드시 Pending 이다

기존 실측값(일지 08-31·09-08·10-08): 노드당 allocatable **1365Mi**, 노드당 DaemonSet **104Mi**.

```
기본 앱 requests   core 480 + daily 512 + ai 320 + postgres 256        = 1568Mi
시스템 Deployment  ebs-csi-controller 232 + coredns 70                 =  302Mi
metrics-server     200Mi (10-08 실측)
Karpenter          256Mi 🟡 (D5)

4대 중 1대는 k6 전용(cordon) ⇒ 앱 노드 3대
3 × (1365−104) = 3783 − 1568 − 302 − 200 − 256 = 1457Mi
daily-api 512Mi 가 더 들어가는 수 = 내림(1457 ÷ 512) = 2
⇒ 복제본 3개까지. **4번째는 배치와 무관하게 총량으로 안 들어간다.**
```

5a 에서는 남는 양이 1713Mi 라 총량으로는 3개가 더 들어갔고, 4번째가 Pending 이 된 것은 배치(조각화)
탓이었다. 이번에는 Karpenter 몫 256Mi 가 빠져 **산술만으로 결정된다.**

Karpenter 가 띄울 노드: `t4g.small` 한 대의 여유는 1261Mi 🟡 → `daily-api` 2개. vCPU 쿼터 32 중
노드그룹 8 + Karpenter 상한 4 = 12.

## 4. 통과 기준

각 항목은 **틀렸을 때 다른 값이 나오는** 검사로 잡는다.

| # | 주장 | 검사 | 통과 | 실패하면 보이는 것 |
|:-:|---|---|---|---|
| P1 | 컨트롤러가 떴고 AWS 권한이 붙었다 | `kubectl -n kube-system get deploy karpenter` · 로그 | `1/1` + 로그에 `AccessDenied`·`WebIdentityErr` 없음 | CrashLoop / 권한 에러 |
| P2 | Karpenter 가 서브넷·보안그룹·AMI 를 찾았다 | `kubectl get ec2nodeclass default -o yaml` 의 `status` | `Ready=True`, 서브넷 1개(영속 AZ)·SG 1개 이상 | `SubnetsReady=False` 등 (P1 만으로는 못 잡는다) |
| P3 | 🔑 **Pending 이 노드를 만든다** | 4번째 복제본 Pending → `kubectl get nodeclaim -w` | NodeClaim 생성 → 노드 `Ready`. **시각 4개 기록**: Pending · NodeClaim 생성 · 노드 Ready · 파드 Ready | Pending 이 그대로 남음 |
| P4 | 고른 인스턴스가 규칙 안이다 | `kubectl get nodeclaim -o wide` · `aws ec2 describe-instances` | `t4g.small`(~~또는 `t4g.medium`~~ — D6 정정), `arm64`, 영속 AZ | 다른 종류·다른 AZ |
| P5 | 그 노드에서 파드가 실제로 일한다 | 새 노드의 `daily-api` 파드 `kubectl top` (연결 재사용 끈 부하) | 유휴보다 뚜렷이 높다 | Ready 인데 유휴 |
| P6 | 비면 사라진다 | 부하 중지 → HPA 축소 → 관찰 | NodeClaim 0, EC2 `terminated`. **시각 기록**: 파드 0 → 노드 삭제 | 빈 노드가 남음 |
| P7 | 🔴 **`tofu destroy` 만으로 고아가 안 남는다** | Karpenter 노드를 **살려 둔 채** `kubectl` 정리 없이 destroy → AWS 직접 조회 | 클러스터 태그 달린 EC2 0 · 인스턴스 프로파일 0 · 시작 템플릿 0 · destroy 에러 없음 | 인스턴스 잔존 / 노드 역할 삭제가 `DeleteConflict` |
| P8 | 뒷정리 | SOP 종료 절차 (8b 완료 게이트 포함) | `tofu state list` 0건, 고아 0 | — |

덤으로 볼 것(통과 기준 아님):

- **E1 — 무엇을 고르는가**: Pending 이 1개일 때와 2개가 동시에 생겼을 때 Karpenter 가 고르는 인스턴스가
  같은가. 로그의 선택 근거 원문을 일지에 남긴다.
- **E2 — 상한**: `limits.cpu: 4` 를 넘기는 요구가 오면 무슨 이벤트가 나오는가. P3 뒤 시간이 남을 때만.

## 5. 계획과 실제 코드 사이의 불일치 (착수 전 조사)

| # | 계획이 가정한 것 | 실제 | 이번에 |
|:-:|---|---|---|
| U-1 | `tofu destroy` 가 다 지운다 | 🔴 **Karpenter 가 만든 EC2·인스턴스 프로파일·시작 템플릿은 state 밖이다.** 클러스터만 지우면 EC2 가 주인 없이 남아 과금되고, 프로파일이 노드 역할을 물고 있어 역할 삭제가 실패한다. 리퍼(`eks-reaper.sh`)는 Ingress 정리 + `tofu destroy` 만 하고, 생존 판정도 EKS·RDS 뿐이다 | D9 — 삭제 시점 실행기. 순서: **노드그룹 삭제(= 컨트롤러 사망) → 정리 → { 노드 역할 · 클러스터 } 삭제.** 컨트롤러가 살아 있을 때 EC2 를 지우면 다시 띄운다. 클러스터보다 먼저여야 하는 이유는 클러스터 보안그룹이다(Karpenter EC2 가 쓰고 있으면 삭제가 걸린다 🟡) |
| U-2 | 공식 안내대로 하면 된다 | 공식 안내는 `eksctl` + CloudFormation + Pod Identity + 스팟 큐 전제다. 이 레포는 OpenTofu + IRSA 다 | CloudFormation 템플릿(v1.14)의 정책 6종 중 **4종**(`NodeLifecycle`·`IAMIntegration`·`EKSIntegration`·`ResourceDiscovery`)을 tofu 로 옮긴다. `Interruption`·`ZonalShift` 는 뺀다 |
| U-3 | 차트 기본값으로 뜬다 | `replicas: 2` + 호스트 단위 필수 반친화성 + AZ 분산 `DoNotSchedule`, requests 없음 | D5. requests 를 안 주면 스케줄러가 0 으로 계산해 §3 산술이 틀어진다 |
| U-4 | 서브넷에 Karpenter 용 태그가 있다 | 없다. `kubernetes.io/role/elb` 와 `kubernetes.io/cluster/<이름>: shared` 뿐이고 두 AZ 모두에 붙어 있다 | D7. AZ 는 NodePool 요구 조건으로 좁힌다 |
| U-5 | 노드는 프라이빗 서브넷에 뜬다 | 이 클러스터는 NAT 가 없고 노드가 퍼블릭 서브넷에 공인 IP 로 뜬다 | 서브넷의 `map_public_ip_on_launch=true` 를 따르는지 P4 에서 확인 🟡 |
| U-6 | k6 격리 노드는 Karpenter 와 무관하다 | `pin-loadgen-node.sh` 가 노드 하나를 cordon 한다. Karpenter 는 자기가 만든 노드만 통합 대상으로 본다 🟡 | 그대로 둔다. 통합이 관리형 노드를 건드리면 그 사실을 기록 |
| U-7 | 새 노드가 뜨면 곧 파드가 일한다 | 노드 준비 + 이미지 풀(새 노드엔 캐시가 없다) + Spring 기동 2~3분 + 5a 에서 본 콜드 JVM 과소비가 겹친다 | 시각 4개를 따로 기록(P3). 어느 구간이 긴지가 결과다 |
| U-8 | HPA 가 4개까지 올려 준다 | 5a 에서는 콜드 JVM 과확장 덕에 4 가 됐다. 재현이 보장되지 않는다 | §6 분기: 5분 안에 4 가 안 되면 HPA `minReplicas` 를 4 로 고쳐 강제한다 |
| U-9 | AMI 는 알아서 맞는다 | EC2NodeClass 는 AMI 별칭을 요구한다. `al2023@latest` 는 새 AMI 가 나오면 노드를 교체하므로 상시 운영에는 버전 고정을 권한다 | 실습은 세션이 1시간이라 `al2023@latest` 로 간다. 튜토리얼에 그 차이를 적는다 |

### 이번에 하지 않는 것 (다음 작업으로 남긴다)

| 항목 | 왜 지금 아닌가 |
|---|---|
| 스팟 + 중단 알림 큐 (SQS · EventBridge 규칙) | 별개 주제. 리소스 6개가 더 생기고 스팟 중단을 일부러 일으킬 방법이 마땅치 않다 |
| Pod Identity 로 전환 | D2. 레포 전체 관례를 바꾸는 일이라 Karpenter 하나로 정할 것이 아니다 |
| 관리형 노드그룹을 최소(1~2대)로 줄이고 나머지를 전부 Karpenter 에 맡기기 | 기본 앱 배치까지 Karpenter 에 걸리면 컨트롤러 자신이 Pending 이 되는 순환이 생길 수 있다. 5a 와의 비교도 깨진다 |
| 다중 AZ 분산 | postgres 볼륨이 단일 AZ 다. `daily-api` 만 다른 AZ 로 보내면 AZ 간 전송 요금이 붙는다 |
| 노드 교체(drift · `expireAfter`) · 중단 예산(disruption budgets) | 1시간 세션에서 일어나지 않는다 |
| 통합이 **덜 찬** 노드를 합치는 장면 | 노드 2대 이상을 띄워 놓고 기다려야 한다. E2 까지 가면 일부 보일 수 있다 |
| 리퍼의 생존 판정에 "Karpenter EC2" 추가 | 하네스 동결. D9 가 통과(P7)하면 필요가 없어진다. **P7 이 실패하면 이 항목이 5c 전 필수가 된다** |
| 콜드 JVM 과확장이 노드까지 과하게 띄우는 문제 | 5a 에서 남긴 항목과 같다. 이번엔 현상이 보이면 기록만 |
| ✅ ~~세션 종료의 승인 대기 — 두 번째 발생~~ (2026-10-12) → **결정됨 (2026-10-13): 규칙 변경 없이 사용자가 지켜보는 시간에만 진행한다**(5c 계획서 D15) | 10-11 대책(시각 확답)이 첫 적용에서 실패했다. 유휴 $0.405. ~~다음 유료 세션(5c) 전에 다시 정한다.~~ 남은 선택지: 실습 레이어 한정 destroy 허용 규칙(10-11 기각) · 리퍼 대기 시간 단축 · 세션을 사용자가 지켜보는 시간에만 진행 |
| P7 의 EC2 종료 경로를 실물로 확인 | Karpenter 가 destroy 시작 35초 뒤 자기 노드를 먼저 지웠다(원인 ⚪). 확인하려면 컨트롤러를 먼저 죽인 뒤 destroy 해야 한다(`kubectl scale deploy karpenter --replicas=0`). 다음 Karpenter 세션이 있으면 그때 |
| Karpenter 가 destroy 직후 노드를 지운 이유 | 그 시각의 컨트롤러 로그를 받지 않았다. 과금 중이라 넘어갔다 |
| `Underutilized` 통합과 HPA 의 진동 | 현상 확인까지. 완화책(`consolidationPolicy: WhenEmpty`, `consolidateAfter` 를 HPA 안정화 창보다 길게, `karpenter.sh/do-not-disrupt`)은 실측하지 않았다 |
| E2 — `limits.cpu` 초과 시 동작 | E1 의 멈춘 NodeClaim 에 가려 못 봤다 |

## 6. 세션 절차 (5b 고유 부분)

SOP 의 시작·종료 체크리스트는 그대로 따른다. 🔴 **과금 구간에서는 질문하지 않는다.**

🔴 **apply 전에 확답받을 것 (SOP §시작 1, 2026-10-11 추가)**: 승인 창은 apply 2번 · destroy 2번.
destroy 승인 예상 시각은 **apply 시각 + 60분**, 그 뒤 4~8분 안에 한 번 더. 이 시각에 자리에 있다는
확답이 없으면 apply 하지 않는다.

```
[$0]  코드 준비 · tofu validate/plan · helm template · 매니페스트 dry-run(client) · 이 계획서 승인
[과금] ① tofu apply -var node_desired_size=4            (전경, 마커 육안 확인)
      ② 기본 앱 기동 · postgres 비밀번호 동기화(SOP 6b) · k6 노드 격리
      ③ 보안그룹 태그 조회(D7 확인) → helm install karpenter → P1 · 컨트롤러 실제 메모리 기록
      ④ EC2NodeClass · NodePool 적용 → P2
      ⑤ HPA 적용 → 부하(연결 재사용 끔) → 4번째 Pending → P3 · P4 · P5 · E1
      ⑥ 부하 중지 → P6
      ⑦ (시간 남으면) E2
      ⑧ 부하 재개로 Karpenter 노드 1대를 다시 띄운 뒤, kubectl 정리 없이 destroy → P7
      ⑨ SOP 8b 완료 게이트 + Karpenter 잔존물 조회 → P8
```

미리 정한 분기:

| 상황 | 행동 |
|---|---|
| ③ 클러스터 SG 에 `kubernetes.io/cluster/devquest-eks: owned` 태그가 없다 | EC2NodeClass 의 SG 선택자를 `id:` 로 바꿔 적용한다(값은 `aws eks describe-cluster` 의 `clusterSecurityGroupId`). 그 사실을 기록 |
| P1 이 5분 안에 안 된다 | 로그 원문을 기록하고 ⑨. 과금 중 원인 분석을 하지 않는다 |
| P2 가 `Ready=False` | `status.conditions` 원문 기록. 위 SG 분기에 해당하면 1회 수정, 아니면 ⑨ |
| ⑤ 5분 안에 HPA 가 4개를 원하지 않는다 | `kubectl patch hpa daily-api` 로 `minReplicas: 4`. 부하 단계를 더 올리지 않는다 |
| P3 에서 NodeClaim 은 생겼는데 노드가 5분 안에 `Ready` 가 안 된다 | NodeClaim 이벤트·컨트롤러 로그 원문 기록 후 ⑧ (그 인스턴스가 P7 의 재료가 된다) |
| P6 에서 빈 노드가 3분 안에 안 사라진다 | 노드의 `kubectl describe` 와 컨트롤러 로그를 기록하고 ⑧로 간다 |
| 🔴 P7 실패 — destroy 뒤 EC2 가 남았다 | 태그 필터로 직접 종료한다(아래). 프로파일이 역할을 물고 있으면 떼고 destroy 재실행 |
| 과금 시작 후 **90분** | 어디까지 했든 ⑧ |

P7 실패 시 수동 회수(미리 적어 둔다):

```bash
R=ap-northeast-2
aws ec2 describe-instances --region $R \
  --filters "Name=tag:karpenter.sh/nodepool,Values=*" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[].Instances[].InstanceId' --output text
aws ec2 terminate-instances --region $R --instance-ids <위 결과>
```

## 7. 비용

| | 시간당 |
|---|---:|
| 컨트롤플레인 | $0.1000 |
| 노드그룹 4대 (`t4g.small` $0.0208 + 공인 IP $0.005 + 루트 EBS $0.0025) × 4 | $0.1132 |
| Karpenter 노드 — 상한 `cpu: 4` = `t4g.small` 2대일 때 | 최대 $0.0566 |
| **합계 (최대)** | **$0.2698** |

예상 70분(Karpenter 노드는 그중 30분 안팎) ≈ **$0.27**, 상한 90분 ≈ **$0.40**.
🔴 5a 는 예상 $0.23 이 $0.59 가 됐다. 원인은 종료 승인 대기였고, 대책은 §6 맨 위의 확답이다.

## 8. 바꿀 파일

| 파일 | 변경 |
|---|---|
| `infra/aws-eks/2-cluster/irsa-karpenter.tf` | 신규. 컨트롤러 역할·신뢰 정책·권한 정책 4종 |
| `infra/aws-eks/2-cluster/iam-policy-karpenter/*.json.tftpl` | 신규 4개. 공식 CloudFormation 템플릿(v1.14)에서 변수만 바꿔 옮겼다 |
| `infra/aws-eks/2-cluster/scripts/karpenter-cleanup.sh` | 신규. 정리 스크립트 본체(사람이 직접 돌릴 수도 있다) |
| `infra/aws-eks/2-cluster/karpenter-cleanup.tf` | 신규. D9 삭제 시점 실행기 + 의존 순서 |
| `infra/aws-eks/2-cluster/nodes.tf` | `depends_on` 에 정리 리소스 추가(삭제 순서용) |
| `infra/aws-eks/2-cluster/outputs.tf` | 컨트롤러 역할 ARN · 노드 역할 이름 |
| `k8s/karpenter/{values.yaml,ec2nodeclass.yaml,nodepool.yaml}` | 신규 |
| `docs/eks-tutorial-steps.md` | Stage 5b 절 (세션에서 동작 확인한 명령만) |
| `docs/eks-migration-log.md` | 세션 중 실시간 |
| `infra/aws-eks/README.md` | 진행 현황 5b 행 (실클러스터 확인 후에만 `✅`) |
| `docs/eks-quizzes/stage-eks-13-karpenter.md` | PR 전 이해도 퀴즈 (필수). **출제 전에 용어부터 설명한다**(5a 교훈) |

`be/`·`fe/`·`.claude/scripts/` 는 건드리지 않는다.

## 9. $0 준비 결과 (2026-10-12)

| 확인 | 결과 |
|---|---|
| `tofu validate` · `fmt` | 통과 |
| `tofu plan -var node_desired_size=4` | **`Plan: 42 to add`** = 5a 의 32 + Karpenter 역할 1 · 정책 4 · 연결 4 + 정리 리소스 1 |
| 삭제 순서 (`tofu graph`) | `노드그룹 → 정리`, `정리 → 클러스터`, `정리 → 노드 역할` 세 간선 확인 |
| 정리 스크립트 — 실계정(지금은 아무것도 없다) | `Karpenter EC2 없음` · `잔존 EC2 0` · exit 0 |
| 정리 스크립트 — 가짜 aws 주입 3종 | 정상: 종료·대기·시작 템플릿·프로파일 삭제 후 exit 0, **EKS 가 만든 프로파일은 건드리지 않음** / 종료해도 남는 경우: exit 1 / 조회 거부: 아무것도 지우지 않고 exit 1 |
| 차트 렌더링 (`helm template`, 1.14.1) | `replicas: 1` · requests `200m/256Mi` · `CLUSTER_NAME=devquest-eks` · 서비스 계정 어노테이션 주입 확인 |
| 매니페스트 ↔ 차트 CRD 스키마 | EC2NodeClass 필수 3필드·`role`·`alias`, NodePool `limits`·`consolidateAfter`·`WhenEmptyOrUnderutilized` 전부 스키마에 있다 |
| 영속 AZ | `tofu output persistent_az` = `ap-northeast-2a` = NodePool 의 AZ 제한 값 |

**실클러스터에서만 확인되는 것** (세션으로 넘긴다): D3 access entry · D5 컨트롤러 메모리 · D7 보안그룹 태그 ·
U-5 공인 IP · 정리 스크립트가 실물 EC2 와 프로파일에 대해 동작하는가(P7).
