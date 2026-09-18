# AWS EKS 학습 놀이터 — 계획

> 2026-07-13 대화에서 확정된 방향을 문서화. 대화 원본은 세션 트랜스크립트에만 있었고 이 문서가 첫 기록.
> 진행 상태는 아래 "진행 현황" 표를 갱신한다.

## 한 줄 정의

> **AWS EKS를 OpenTofu로 세웠다 부수는 K8s 학습 놀이터.** destroy-after-use + 신규계정 $200 크레딧.
> **프로덕션은 Fly.io($0) 그대로.** **인프라 전부를 코드로 — 콘솔 클릭 0 지향 (IaC-first, 2026-07-16 확정).**

## ⚠️ 이건 "프로덕션 이관"이 아니다

이 점이 가장 오해하기 쉽다. 프로덕션 이전은 **검토했고 명시적으로 기각**했다:

| | 내용 |
|---|---|
| 검토안 | 블루-그린으로 `api.quest.dhbang.co.kr`를 Fly → AWS ALB로 flip |
| 비용 | ECS Fargate 상시 prod ≈ **월 $35** (ALB $16 고정 + Fargate 1GB $18 + ECR/데이터 $2) |
| 기각 사유 | $200 크레딧 ÷ $35 = **5.7개월** → "6개월 뒤 월 $35 청구서"로 문제를 **없앤 게 아니라 이연**. 이 프로젝트의 출발점이 "$5.7/월 스케일업도 안 쓴다"였는데 그 **6배** |
| 추가 사유 | $200을 상시 prod에 태우면 **띄웠다 부수는 실험에 쓸 크레딧이 소진됨**. prod를 Fly에 두면 같은 $200으로 6개월 내내 실험 가능 |

**결론: prod = Fly($0) 유지. AWS = 학습 전용, 안 쓸 땐 destroy.**

> 나중에 진짜 이관하고 싶어지면 그때 별도 판단.
>
> 🔴 **이 선행 조건은 2026-09-18 에 재정의됐다.** 종전 문구는 *"메타스페이스 누수 검증
> (`.claude/CONTEXT.md` 참조) — 누수면 AWS 로 옮겨도 따라간다"* 였는데 **두 군데가 틀렸다**:
> ① **메타스페이스 누수는 없다** — 2026-07-15 조사 종결(근거 3종). 이 조건은 **이미 충족**이다.
> ② 포인터가 깨졌다 — 내용이 `docs/jvm-observability-notes.md` 로 이관됐다(#427).
>
> **살아 있는 선행 조건은 다른 것이다**: **RSS creep + K8s 에 swap 이 없다**(계획서 B-5).
> 메타스페이스와 RSS creep 은 *"두 리스크는 별개"* 로 명시 판정돼 있다(0.73 vs 3 MB/h).
> 2026-09-18 실측: Fly 실효 **715 MiB**(RAM 459 + swap 256) vs K8s **576 MiB** → **139 MiB 적다.**
> 상세·재측정 절차는 `docs/jvm-observability-notes.md` §6.

---

## 왜 이 선택인가 (기각안 포함)

### 왜 AWS인가

| 후보 | 판정 | 사유 |
|------|:--:|------|
| **AWS** (서울 ap-northeast-2) | ⭐ **채택** | Terraform 프로바이더 표준·가장 성숙, 서울 리전 = 레이턴시 최상, **이력서 가치 압도적**(VPC·IAM·ALB·EKS). $200/6개월 크레딧 |
| Hetzner (싱가포르) | ✗ | 월 $4 정액·빌쇼크 없음·용량난 없음으로 **비용 안정은 최고**. 단 스킬 이전성 낮아 이력서 가치 중간. 신규 KYC 마찰(신분증) |
| Oracle Cloud Always Free | ✗ | 2026-06 ARM 무료가 **4OCPU/24GB → 2/12로 반토막**(무공지) + 도쿄/오사카 **프로비저닝 사실상 불가** + 50Mbps 스로틀 + 7일 유휴 회수. PAYG 면제도 공식 보장 아님 → **불안정 자산으로 강등** |
| Google Cloud Run | ✗ | scale-to-zero → 콜드스타트 상시화 |
| Render / Koyeb / Railway | ✗ | Render 512MB+15분 sleep / Koyeb 무료 컴퓨트 폐지 / Railway $5 크레딧 1~2주 소진 |

> 맥락: 이 탐색의 원래 동기는 "Fly 512MB OOM 탈출"이었으나, **#263~#265로 OOM은 $0에 해결**됐다.
> 따라서 이 트랙의 동기는 **생존이 아니라 학습·이력서**다. 그래서 "무료 RAM 크기"보다 **학습 가치**가 선택 기준이 됐다.

### 왜 ECS Fargate가 아니라 EKS인가

**ECS는 쿠버네티스가 아니다.** AWS 독자 오케스트레이터다.

| | ECS Fargate | **EKS** |
|--|---|---|
| K8s 학습 | ❌ kubectl·pod·manifest·K8s API 전부 없음 | ✅ 정통 |
| kind 트랙(Stage 1~4) 연계 | ❌ 없음 | ⭐ **직결** |
| 이력서 | 중 | ⭐ 시장에서 K8s >> ECS |
| 복잡도 | 낮음 | 높음 (= 배울 게 많음, 양날) |
| 띄우고 부수기 속도 | 빠름(수 분) | **느림 — up/down 각 ~15분** |
| 세션 비용 | <$1 | ~$0.3~2 |

- 개념적 유사물은 있으나(Task≈Pod, Service≈Deployment, TargetGroup≈Ingress) **실제 스킬은 이전 안 된다.**
- EKS 컨트롤플레인 $0.10/시간 → **3시간 세션 = $0.30.** 크레딧으로 30시간 실습해도 ~$3.
  ("EKS 월 $73"은 **상시 운영** 기준이라 destroy-after-use 놀이터엔 해당 없음. 현재가는 착수 시 재확인 권장 🟡)
- EKS의 유일한 실질 단점: 클러스터 생성/삭제가 각 ~15분 → "잠깐 놀기"보다 **한 세션 잡고 하기**에 적합.

### 왜 Fargate 프로파일이 아니라 관리형 노드그룹인가

> **노드 = 관리형 노드그룹 (t4g.small Spot ×1~2).**
> 사유: Stage 3 목표가 **Postgres StatefulSet + EBS 영구볼륨**인데 **EKS Fargate는 EBS를 못 붙인다**(EFS만 지원).
> StatefulSet 학습하려면 EC2 노드가 필수. 🔴

---

## 아키텍처 — 2계층 IaC

**전부 IaC 가능하다. 수동 클릭 0.** 단 "한 도구"가 아니라 두 계층으로 나뉜다.

| 계층 | 도구 | 대상 |
|------|------|------|
| **1. 클라우드 인프라** | **OpenTofu** | VPC·서브넷·IGW, EKS 클러스터, 노드그룹, IAM/**IRSA**·OIDC, EKS 애드온(`aws_eks_addon`: EBS CSI·CoreDNS·kube-proxy·VPC CNI) |
| **2. K8s 워크로드** | 선언형 매니페스트 (kubectl → Helm → ArgoCD) | Deployment·Service·Secret·StatefulSet·PVC·Ingress, Helm 애드온(AWS LB Controller) |

### ⚠️ state를 둘로 쪼갠다 (비자명)

"한 `tofu apply`에 워크로드까지 다 넣기"는 **되지만 날카로운 모서리**가 있다:

- `kubernetes`/`helm` 프로바이더는 **클러스터가 이미 존재 + 자격증명**을 요구한다.
- 같은 apply 안에서 EKS 출력으로 프로바이더를 구성하면 → **plan 타임 값 미확정 에러**, destroy 순서 꼬임이 흔하다.
- 그래서 실무는 **인프라 state ↔ 워크로드 state를 분리**한다. 아래 리포 구조가 이걸 반영.

> 학습 팁: 한 스테이지는 **일부러 통합 방식**으로 해봐서 이 함정을 손으로 느껴보는 것도 좋다.

### 왜 OpenTofu인가

Terraform 오픈소스 포크, 커뮤니티 기본값이 됨. **HCP Terraform 무료 SaaS는 2026-03-31 종료**(500리소스 캡).

### IaC-first 레이어 구조 (2026-07-16 확정) — "인프라 전부를 코드로"

콘솔 클릭 0이 목표. 엔터프라이즈 실무처럼 **레이어별 state 분리**로 간다.

| 레이어 | 무엇 | 수명 | destroy |
|:--:|------|------|:--:|
| **0-bootstrap** | remote backend(S3+DynamoDB 락)·GitHub OIDC·IAM 베이스라인·**예산(`aws_budgets_budget`)**·이상탐지·**ECR(+lifecycle)** | long-lived | ✗ (계정 상주, ~$0) |
| **1-network** | VPC·서브넷·IGW (NAT 금지) | 김 | 보통 ✗ |
| **2-cluster** | EKS·관리형 노드그룹(t4g.small)·IRSA·애드온 | 세션 | ⭐ **여기만 destroy** |
| **gitops** | ArgoCD app-of-apps → 워크로드(Deployment·StatefulSet·Ingress) | GitOps | (앱) |

- **두 평면 분리**: 클라우드 자원=OpenTofu / K8s 워크로드=GitOps(ArgoCD). terraform kubernetes provider로 워크로드 안 넣음.
- **CI/CD**: PR 열면 `tofu plan`을 코멘트로, 머지 시 `apply` (GitHub Actions + **OIDC**, 장기 액세스키 없음). **tfsec/Checkov 게이트**.
- **부트스트랩 닭-달걀**: backend S3 버킷·예산은 backend 없이 **로컬 state로 1회 apply → backend로 migrate**.
  (지금 콘솔로 만든 예산이 이 수동 프롤로그. 이후 코드로 `import` 또는 재생성해 승격.)
- **크레딧 제외**: 예산 코드에 `cost_types { include_credit = false, include_refund = false }` —
  콘솔에서 막힌 "Cost Explorer 데이터 없음"을 우회, 즉시 적용 (기존 TASK-5 불필요화).

#### ⚠️ public repo + IaC — state 보안 (절대 규칙)
- **tfstate엔 평문 secret이 들어간다 → git 커밋 절대 금지.** `*.tfstate*` gitignore + **S3 remote backend**(암호화·버저닝·접근제어).
- account ID·ARN 등 식별자도 `*.tfvars`(gitignore)로 분리. 하드코딩 금지.
- **secret 감지는 두 층 분리**: 코드단 gitleaks(CI) / 인프라단 tfsec·Checkov(CI). 대상·도구가 다름.

---

## 학습 스테이지 (레이어 2-cluster + gitops 세부)

> ⚠️ kind 로컬 트랙은 07-16 폐기(#269) — 아래 "kind 대응" 열은 참고용 잔재. 실작업은 EKS 단독.


| Stage | 세울 것 | 새로 배우는 EKS 고유 개념 | kind 대응 |
|:--:|---|---|---|
| **0** | OpenTofu로 VPC + EKS 클러스터 + 관리형 노드그룹(t4g.small Spot ×1~2) + `kubectl` 연결 | 컨트롤플레인, 노드그룹, OIDC | (신규) |
| **1** | Deployment + Service로 앱 배포 (ECR 이미지) | ECR·이미지 풀, 실클러스터 `kubectl` | Stage 1 재사용 |
| **2** | Secret / Config | **IRSA** (IAM Roles for ServiceAccount) — EKS의 정수 | Stage 2 재사용 |
| **3a** | **Postgres StatefulSet + EBS CSI + 동적 PVC** | StorageClass, 동적 EBS 프로비저닝, `volumeClaimTemplates` | Stage 3 직결 |
| **3b** | 같은 StatefulSet을 **terraform 소유 EBS + static PV**로 전환 | `volumeHandle`·`claimRef`, PV 재바인딩, AZ 종속 | (kind 불가 — EBS 없음) |
| **4** | **AWS Load Balancer Controller → ALB Ingress** | IngressClass, ALB target-type | Stage 4 직결 |
| **5** (선택) | metrics-server·HPA, Karpenter, ArgoCD | 오토스케일, GitOps | (확장) |

### 진행 현황

| Stage | Status | 완료일 | 실측 결과 |
|:--:|:--:|---|---|
| **0** — VPC·클러스터·노드그룹·kubectl | ✅ | 07-24 | 왕복 ~50분. NAT 0개 유지 |
| **1** — ECR 이미지로 Deployment·Service | ✅ | 07-27 | 이미지 풀 성공. DB 미연결이라 CrashLoop로 종료(의도) |
| **2** — Secret/Config + **IRSA** | ✅ | 07-28 | RDS + Secrets Manager + ESO. 아래 주석 참조 |
| **3a** — Postgres StatefulSet + EBS CSI + 동적 PVC | ✅ | 07-30 (#349) | 27분 · ~$0.06. Flyway 12개가 in-cluster에 적용되고 `/health` 200 — RDS와 **동일 결과를 같은 이미지 sha로 재현**. 파드 강제 삭제 후 UID는 바뀌었는데 PVC·데이터(26행)는 그대로 = StatefulSet의 존재 이유를 실측. `kubectl delete -f k8s/base/` 뒤에도 EBS가 `in-use`로 남고, **PVC를 지워야** 7초 뒤 회수됨 <!-- verify: k8s/base/postgres.yaml ~ volumeClaimTemplates --> |
| **3b** — 같은 StatefulSet을 static PV·영속 EBS로 전환 | ✅ | 코드 07-31 (#353)<br>**검증 08-07** | **클러스터를 30개 리소스째 destroy했는데 데이터가 그대로 붙었다.** 재구축 후 `Skipping initialization`(= `initdb` 0회) + `database system was shut down at 15:17:08`(이전 클러스터의 종료 기록) + 증거행·질문뱅크 26행 유지. 파드 UID·노드·클러스터가 전부 다른데도. 세션 97분 ≈ $0.21 <!-- verify: k8s/base/postgres-static.yaml ~ volumeHandle --> <!-- verify: infra/aws-eks/0-bootstrap/ebs-postgres.tf ~ aws_ebs_volume --> |
| ↳ **3b에서 드러난 결함** | 🔴 | 08-07 | **데이터는 붙는데 자격증명이 안 붙는다.** `random_password.postgres`가 `2-cluster` state에 있어 destroy→재apply에서 새로 생성되는데, postgres는 `POSTGRES_PASSWORD`를 `initdb` 때만 쓰므로 옛 해시를 유지 → `FATAL: password authentication failed`. **볼륨과 수명이 같아야 하는 것은 볼륨과 같은 레이어에 둔다**(D-004가 EBS에 적용한 논리). 원장 **L-14** |
| **4** — AWS LB Controller → ALB Ingress | ✅ | 2026-09-11 | **30분 44초 · $0.0996 · 고아 0.** ADDRESS 가 **20초** 만에 채워진 것이 `target-type: ip` 의 증거(기본 `instance` 였다면 ClusterIP 라 영영 비었다). 타겟이 **파드 IP**(10.0.3.131:8080 · 10.0.12.186:8082)로 등록 = `ip` 모드 실물. ALB priority 가 Ingress 배열 순서대로(`1:/api/v1/daily-question→daily` `2:/api/v1→core`). `/actuator/health/readiness` **404** = 인터넷 노출 차단. 🔴 **응답으로는 라우팅을 판정할 수 없었다** — 두 서비스가 같은 DB 행을 읽어 본문이 동일했고 액세스 로그도 없다 → **반증 실험**(daily-api 를 0 으로 내리니 그 경로만 503)으로 확정. 🔑 teardown 에서 `delete ingress` 가 **16초** 걸린 것이 finalizer 의 실물 증거 <!-- verify: k8s/base/ingress.yaml ~ io/target-type:[[:space:]]*ip --> |
| **4b** — ACM HTTPS 종료 (선행 조건 3 ⓓ) | ✅ | 2026-09-16 | 🔴 **목표 6/6 · 비용 21~24배 초과 ($0.13 예상 → $2.77~3.14 실측).** **진짜 HTTPS**: `--resolve` 로 DNS 레코드 없이 `-k` 없이 통과 — `CN=eks.quest.dhbang.co.kr` / Amazon RSA 2048 M04 / TLSv1.2 / h2. **ALB IP 2개 모두 동일**(200/403/**404**/404/404)이라 n=1 판정이 아니다. `ssl-redirect` → HTTP 전부 301, 80 리스너는 `default→redirect` 하나뿐이라 🔴 `Listeners[0]` 로 규칙을 읽으면 우선순위 표가 안 나온다(443 필터 필수). **2a 체인 확정**: 파드 `JWT_SECRET` 지문 = `0-bootstrap` learning 키(prod 와 다름) → 4단계를 한 번에 판정. **경계 실증**: suffix·리전·계정 동일, 이름만 prod → `allowed` ↔ `implicitDeny` (가짜 suffix 로는 **둘 다 거부돼 판정력 0** 이었다). ACM `RenewalEligibility` 는 붙으면 `ELIGIBLE`, teardown 하면 **`INELIGIBLE` 로 되돌아온다.** 🔴 **사고: `destroy` 를 배경으로 보내고 완료를 확인하지 않아 15.8시간이 샜다** — SOP §8b(완료 게이트)·§5(전경 실행) 신설. `tofu apply` 가 세션 마커를 안 만든 것도 같은 세션에서 발견(원장 L-58) <!-- verify: k8s/base/ingress.yaml ~ io/certificate-arn:[[:space:]]*CERT_ARN_PLACEHOLDER --> |
| **5** — metrics-server·HPA·Karpenter·ArgoCD | ⬜ | — | 선택 |

> 🔴 **`✅`는 "머지됐다"가 아니라 "실클러스터에서 확인했다"는 뜻이다.** 3b가 `🚧 코드만`인 이유가
> 그것이다 — PR은 머지됐고 CI가 apply까지 했지만, 이 Stage의 **학습 목표 자체**(부수고 다시 지어도
> 데이터가 붙는가)는 유료 세션 없이는 확인할 수 없다. 머지를 완료로 적으면 표가 실제보다 앞서 나가고,
> 그건 이 레포에서 반복된 실패 형태(**검사가 주장보다 헐거움**)의 문서판이다.
> ⚠️ 이 표는 실제로 08-07까지 3a·3b를 `🚧`/`⬜`로 두고 있었다 — 3a는 07-30에 끝났는데도.
>
> **Stage 2가 Stage 3를 대체하지 않는다.** RDS(관리형)는 EBS·PVC·StorageClass를 안 건드리므로
> Stage 3의 학습 목표와 겹치지 않는다. Stage 3에서 DB를 in-cluster StatefulSet으로 바꾸면
> 앱 코드 변경 0으로 두 방식을 비교하는 실습이 된다(`application-prod.yml`이 100% 환경변수 기반).

---

## 리포 구조

```
infra/aws-eks/
  README.md              # 이 문서 — 계획 + up/down 절차 + 비용 체크리스트
  0-bootstrap/           # remote backend·OIDC·IAM·예산·이상탐지·ECR  (long-lived, 별도 state)
  1-network/             # VPC·서브넷·IGW  (별도 state)
  2-cluster/             # EKS·노드그룹·IRSA·애드온  (별도 state; destroy 대상)
  gitops/                # ArgoCD app-of-apps → 워크로드 매니페스트(Helm/Kustomize)
  # 각 레이어: providers.tf variables.tf outputs.tf backend.tf
  # *.tfstate*, *.tfvars → .gitignore (⚠️ public repo)
```
> 정답 경로 절차는 `docs/eks-tutorial-steps.md`, 실패·결정 이력은 `docs/eks-migration-log.md`.

---

## ⚠️ Teardown 함정 — 돈 새는 지점 (필수 각인)

**`tofu destroy`만으로 안 지워지는 것들이 있다.**

| 잔존물 | 왜 | 대응 |
|---|---|---|
| **ALB / 타겟그룹** | AWS LB Controller가 만든 ALB는 **OpenTofu가 모른다**(state 밖) | `tofu destroy` **전에** `kubectl delete ingress` 먼저 |
| **EBS 볼륨** | PVC의 reclaim 정책에 따라 잔존 | StatefulSet·PVC 삭제 확인 |
| ECR 이미지, CloudWatch 로그, 미연결 EIP | 소액이나 누적 | 주기 확인 |

**정석 순서:**
```bash
kubectl delete ingress,pvc --all -A    # ① LB·EBS 먼저 회수
tofu destroy                            # ② 인프라
tofu state list                         # ③ 비었는지 확인
# ④ AWS 콘솔에서 LB·EBS·EIP 잔존 육안 확인
```

> ECS 태스크를 0으로 줄이는 것만으론 **ALB가 계속 과금**된다(~$0.5/일). 세션 끝나면 **통째 destroy**가 정석.

---

## 비용 가드레일 (크레딧이라도 필수)

- **AWS Budgets 알림** $10 / $50 / $150 + **Cost Anomaly Detection** — 착수 전 먼저 설정
  → **둘 다 `0-bootstrap` 코드로 생성된다** (`budget.tf` / `cost-anomaly.tf`). 콘솔 절차는 폐기.
  코드화의 핵심 효용은 자동화가 아니라 **함정 차단**이다 — `include_credit=false`(크레딧 상계 방지),
  `threshold_type=ABSOLUTE_VALUE`(콘솔 기본값 %의 함정)를 사람이 틀릴 여지 없이 고정한다.
  - ⚠️ 둘 다 **실시간 아님** (예산=ACTUAL 청구 반영 ~24h, 이상탐지 EMAIL=`DAILY`가 최선.
    `IMMEDIATE`는 SNS 전용). 30분 세션의 실시간 방어는 **리퍼**가 하고, 이 둘은 마지막 그물.
- **NAT Gateway 절대 회피** (+$32/mo 폭탄) → **퍼블릭 서브넷 + 노드 공인IP**로 구성
- **ARM64** (t4g / Graviton) — 우리 alpine 이미지 arm64 호환, x86보다 ~20% 저렴
- **Spot 인스턴스** 노드그룹 — 학습용이라 중단 감내 가능
- 세션 종료 = `tofu destroy` → `tofu state list` 비었는지 확인 (위 Teardown 순서 준수)
- 크레딧 소진율 주기 확인. **크레딧 = 해결이 아니라 유예**임을 기억

> ⚠️ 2025-07 이후 신규 AWS 계정은 **Free Plan** 구조 — 크레딧 소진 시 Paid Plan 업그레이드 안 하면
> 리소스가 중단될 수 있다. 정확한 만료 동작은 **가입 시 직접 확인** 필요 (신정책이라 케이스별 상이 🟡).
>
> 🔴 **치명적 함정 (07-16 실측)**: **AWS Organizations를 만들면 Free Plan → Paid 전환되며 크레딧이
> 즉시 소멸**한다. IAM **Identity Center(SSO)를 켜는 기본 경로가 org를 생성**하므로 **누르지 말 것**.
> 자격증명은 **IAM 사용자 액세스키**로 (org 불필요). 경고 원문·상세: `docs/eks-migration-log.md`.

### 계정 관련 메모

기존 계정 프리티어는 소진됨. **새 이메일로 계정 신설 시 $200/6개월 크레딧** 대상.

> ✅ **07-16 실측**: 계정 신설 완료. 크레딧은 $200 단일이 아니라 **$100 기본 + $20×5 활동** 분할 지급.
> 🔴 **07-29 정정 — 만료는 `2027-01-15`다.** 07-16에 여기 `2027-07-15 (가입 +1년)`으로 적었던 것은
> **오독이었다**(콘솔 값이 아니라 "가입 +1년"이라는 추론이 값을 덮어썼다). 사용자 콘솔 재확인으로 확정.
> 즉 크레딧 수명은 **1년이 아니라 약 6개월**이고, 원래 계획 문서의 "6개월" 표기가 맞았다.
> 상세: `docs/eks-migration-log.md`.

> 단 **IaC 학습엔 프리티어가 사실 필요 없다** — "띄우고 → 만지고 → `destroy`" 사이클은 시간당 과금이라
> 기존 계정에서도 세션당 몇 센트~몇 달러다. 크레딧 노린 계정 갈아타기 반복은 ToS 정신에 걸리고,
> destroy 습관이 있으면 실익도 미미. **기존 계정 + 가드레일**도 충분히 합리적.

---

## 착수 순서 (IaC-first)

- [x] AWS 신규 계정 + 콘솔 예산(수동 부트스트랩 프롤로그) — 07-16 완료
1. **0-bootstrap** — 로컬 state로 S3 backend 버킷 + DynamoDB 락 생성 → `backend.tf`로 migrate.
   이어서 예산(`aws_budgets_budget`, 크레딧 제외 포함)·GitHub OIDC·IAM 베이스라인 apply.
   → 콘솔 예산은 여기서 `import` 또는 삭제 후 코드판으로 대체.
2. **CI 파이프라인** — `.github/workflows`에 plan-on-PR(+tfsec) / apply-on-merge(OIDC).
3. **1-network** — VPC apply.
4. **2-cluster** — EKS + 노드그룹 + `kubectl` → **즉시 `destroy` 1회 왕복**으로 teardown 체득.
5. **gitops** — ArgoCD로 워크로드 배포 (Deployment → Secret/IRSA → StatefulSet+EBS → ALB Ingress).
6. **재현 검증 (완료 조건)** — 전체 destroy → `docs/eks-tutorial-steps.md`만 보고 처음부터 재현.
   막히면 문서 수정 후 재시도, 통과해야 완료 (블로그 공개 전 정확성 검증).

## 참조

| 주제 | 위치 |
|------|------|
| 정답 경로 튜토리얼 | `docs/eks-tutorial-steps.md` |
| 작업 일지 (결정·실패·비용) | `docs/eks-migration-log.md` |
| K8s 학습 로드맵 (전체 10단계) | wiki `tech/k8s/_roadmap.md` |
| 현재 작업 상태·미해결 이슈 | `.claude/CONTEXT.md` |

## 결정 기록 (📌 D-)

> **2026-09-18 이관.** 원래 `.claude/CONTEXT.md` 에 있었으나 그 파일이 1017줄로 80줄 규칙을
> 12.7배 위반해 주제별로 재배치했다(원장 L-10).
>
> 🔴 **왜 새 `docs/` 문서가 아니라 여기인가**: `check-design-integrity.sh` 의 `DOCS` 는 **고정 목록**이고
> 이 README 는 거기 들어 있다. 새 파일로 옮겼으면 결정 블록이 무결성 검사 **밖**으로 나갔을 것이다 —
> 그 스크립트가 스스로 적어둔 사각지대(*"결정이 사는 곳이 감시 밖이면 무결성 검사의 의미가 없다"*)를
> 재생산할 뻔했다.
>
> 📖 비용 상수·전략·Free Plan 은 `docs/eks-cost-model.md` 로 갔다(📌 없는 참조 자료라 안전).

#### 상시 운영은 기각 — 자기 선례와 충돌
> 📌 **D-002** · 상태 `✅유효` · 영향 `docs/eks-session-sop.md`, `.claude/scripts/eks-reaper.sh`, `infra/aws-eks/2-cluster`, `infra/aws-eks/README.md`, Stage 0~5 전체

> ⚠️ **이 결정이 뒤집히면 destroy-after-use 규율 전체가 무너진다.** 리퍼(dead man's switch)·SOP의
> 세션 왕복 절차·`2-cluster`를 CI 매트릭스에서 빼둔 `guard-local-layers`가 전부 이 결정의 파생물이다.
> **재채택 유혹이 실재하는 결정**이다 — "잠깐만 켜두면 편한데"가 곧 월 $122~174다.
> 크레딧 잔액이 남아 보일 때 특히 흔들린다. 뒤집으려면 아래 표의 숫자를 **다시 실측**하고
> `design-change-procedure.md` 전 단계를 밟을 것.
| | Fargate (이미 명시적 기각) | EKS 상시 |
|---|---|---|
| 월 비용 | $35 | **$122~174** |
| $200 크레딧 수명 | 5.7개월 | **5~7주** |

README에서 "월 $35 = 5.7개월이라 절벽"이라며 기각한 안보다 **3.5~5배 비싸고 4배 빨리 끝난다.**

#### ✅ 확정 전략: destroy-after-use로 6개월 풀 사용 (크레딧 만료 6개월)
**제약은 돈이 아니라 시간이다.** 버퍼(스팟 최고가 + 잡비 10%) 적용 시간당 단가:

| 모드 | 시간당 | 용도 |
|------|-------|------|
| t4g.small ×1, ALB 없음 | **$0.13** | 인프라 학습(NetworkPolicy·RBAC·Helm·ArgoCD) — nginx 파드로 충분 |
| t4g.medium ×1, ALB 없음 | $0.16 | 실앱 배포 |
| **t4g.medium ×2 + ALB (풀 3서비스)** | **$0.25** | Phase 3 토폴로지 |

**주 25시간 × 26주(650h) 기준**: 인프라 학습 위주 $110 · 항상 풀 토폴로지 $163.
→ **6개월 내내 헤비하게 써도 크레딧이 남는다.** 다 태우려면 주 38시간 필요(비현실적).
- **삽질 비용**: 클러스터 완전 재생성 ≈ **$0.07** / 4시간 세션 $0.64 / 8시간 삽질 $1.28.
  **실패는 사실상 공짜** — 아낄 것은 크레딧이 아니라 "켜놓고 딴짓하는 시간".
- **절감 레버**: ①ALB는 필요할 때만(전체의 25%) ②인프라 학습은 t4g.small ③세션을 **길게 가끔**
  (생성 10~15분+삭제 10분 = 회당 25분 오버헤드 상각) ④**kind 부활 불필요** — 돈이 제약이 아니므로 07-16 폐기 결정 유지
- **잔액 활용**: 남는 크레딧으로 **막판 기간 한정 상시 데모**(3~6주)를 사서 구직·면접 시즌에 맞춤.
  끝나면 destroy → 영구 비용 0, prod는 Fly 복귀.

#### 영속 레이어 — 싸다, 반드시 분리할 것
> 📌 **D-004** · 상태 `✅유효` · 영향 `infra/aws-eks/0-bootstrap/ebs-postgres.tf`, `infra/aws-eks/1-network/outputs.tf`, `infra/aws-eks/2-cluster/nodes.tf`, `infra/aws-eks/2-cluster/addons.tf`, `infra/aws-eks/2-cluster/remote-state.tf`, `infra/aws-eks/PERSISTENT-RESOURCES.md`, `docs/eks-session-sop.md`, `k8s/base/postgres-static.yaml`, `k8s/README.md`, Stage 3a·3b · 재판정 `docs/eks-migration-log.md` 07-30 "EBS 2단계 확정 — 3a 동적 → 3b static"

> ✅ **07-31 진행**: 3a 완료(#349), **3b 구현 완료**. `🔄부분무효`였던 이유(=동적 PVC를 배제한 서술)는
> "배제가 아니라 순서"로 정리돼 해소됐다. 두 Stage 모두 코드에 살아 있다(`postgres.yaml` ↔ `postgres-static.yaml`).
> **3b에서 새로 확정된 것 2가지**(원 결정에 없던 것):
> ① **EBS는 `0-bootstrap`에 둔다** — `2-cluster/variables.tf`가 "이 레이어로 올라온다"고 적어뒀으나
>    그러면 **리퍼가 6개월 데이터를 자동 삭제**한다(dead man's switch는 2-cluster를 destroy한다).
>    `prevent_destroy`로 막으면 리퍼의 destroy가 통째로 실패해 안전장치가 벽돌이 된다.
> ② **노드그룹을 영속 볼륨과 같은 AZ로 고정** — 원 결정에 AZ 얘기가 없었는데, EBS는 AZ 리소스라
>    이게 없으면 **50% 확률로 파드 영구 Pending**이다(3a는 `WaitForFirstConsumer`가 가려주고 있었다).

> 🔄 **07-30 재판정 — "동적 PVC 아님"이라는 배제가 무효화됐다.** 이 블록은 static PV를 택하면서
> **동적 프로비저닝을 명시적으로 배제**했는데, 같은 CONTEXT의 Stage 3 서술과 `README:128`은
> *"StorageClass·동적 EBS 프로비저닝"*을 학습 목표로 적고 있었다 — **정면 충돌이 방치돼 있었다.**
> (Stage 3 착수 전 절차 2단계 조회에서 발견. 이 블록엔 메타 줄이 없어 그동안 아무도 못 잡았다.)
>
> **확정: 배제가 아니라 순서다.** Stage 3을 둘로 쪼갠다.
> | | 무엇 | 데이터 수명 | 왜 이 순서인가 |
> |---|---|---|---|
> | **3a** | StorageClass + `volumeClaimTemplates` (동적) | 세션 휘발 | "PVC가 EBS를 만든다"를 **눈으로 본 뒤**에야 `volumeHandle`이 무슨 뜻인지 이해된다 |
> | **3b** | terraform 소유 EBS + static PV | 6개월 영속 | 3a→3b 전환 과정에서 **실패 ④claimRef 잔존**을 공짜로 만난다 |
>
> 아래 "static PV가 어려운 쪽이라 학습가치가 높다"는 판단은 **유지**된다 — 다만 그게
> "쉬운 쪽을 건너뛸 이유"는 아니었다. 큰 태스크는 쪼갠다(Phase 1 회고).
> ⚠️ **3a 동안에는 `kubectl delete pvc --all -A`가 destroy 전 필수**(SOP §8). 3b에서 EBS가
> terraform 소유로 넘어가면 그때 이 규율이 볼륨엔 적용되지 않는다 — 두 Stage의 teardown이 다르다.

**월 약 $2.3 / 6개월 $14 (크레딧의 7%)**: ECR 5GB $0.50 + EBS 20GB $1.82 + S3/DynamoDB ≈$0.
- **🔴 ECR 구멍**: `README:101,148`은 ECR을 **`2-cluster`(destroy 대상)** 소속으로 적어놨으나
  **실제 `.tf`엔 `aws_ecr_*` 리소스가 0건**(전수 grep). 계획대로 두면 **destroy마다 이미지 전멸**
  → 세션마다 Spring Boot 이미지 3개 재빌드·재푸시(5~10분×3) = destroy-after-use의 실질 마찰.
- **→ ECR은 `0-bootstrap`에 편입**(2026-07-22 확정). 새 레이어(`1-shared`) 신설안은 **폐기** —
  영속 대상이 ECR 하나뿐이라 레이어를 늘리면 `tofu init/apply` 대상과 CI 매트릭스만 증가한다.
  0-bootstrap은 이미 **계정 수준 공유·영속 인프라**(S3 state·DynamoDB·OIDC·IAM·예산)를 담고 있어
  성격이 같고, `infra-deploy.yml` 매트릭스에 이미 있어 **CI 변경도 불필요**. **lifecycle policy 필수**(무한 누적 방지).
- **EBS는 terraform이 소유하고 K8s는 static PV로 바인딩** ~~(동적 PVC 아님)~~ **→ 07-30 정정:
  동적 PVC를 배제하지 않는다. 3a에서 동적으로 먼저 배우고 3b에서 이 구성으로 전환한다(위 D-004).**
  근거: ①IaC-first 원칙
  ②ALB 고아와 같은 실패 모드 원천 차단 ③**학습 가치** — 동적 프로비저닝은 쉽고, 어려운 건
  "이미 있는 볼륨에 StatefulSet 붙이기"(`volumeHandle` static PV). **부수고 다시 지어도 데이터가
  그대로 붙는 것**을 확인하는 게 진짜 교보재.
- **EBS를 6개월 영속 유지한다** (월 $1.82 = 6개월 $11 = 크레딧 5.5%). *"한 번 확인하면 끝"*이라는
  초안 판단은 **철회** — 학습은 반복에서 나오고, **아래 실패 6종은 여러 번 밟아야만 만난다.**
  ⭐ **destroy-after-use 규율이 희소한 반복 기회를 공짜로 만든다**: 보통 학습자는 클러스터를 부술
  이유가 없어 이 경험 자체를 못 한다(kind는 EBS가 없고, 회사에선 플랫폼팀이 소유). 우리는 비용 때문에
  **어차피 매번 부수므로**, 그 사이클에 볼륨 재바인딩을 얹으면 **추가 작업 없이 매 세션 연습**된다.
- ⚠️ **반복해야만 만나는 실패 6종** — 번호 정의는 `infra/aws-eks/PERSISTENT-RESOURCES.md`
  「실패 6종」 표가 **유일 출처**다(레포 23곳이 번호로만 참조한다). 요약·재번호 금지.


#### 🔴 DB 전략 — 환경별 분리 확정 (2026-07-22)
> 📌 **D-001** · 상태 `🔄부분무효` · 영향 `infra/aws-eks/2-cluster/rds.tf`, `docs/eks-tutorial-steps.md`, `infra/aws-eks/README.md`, Stage 2·3 · 재판정 `docs/eks-migration-log.md` 07-28 "RDS를 destroy-after-use로 Stage 2에 편입"

> **prod(Fly)는 Neon 그대로. EKS 학습 클러스터에서만 in-cluster PostgreSQL + 영속 EBS.**

> 🔄 **07-28 재판정 — 아래 "RDS 재탈락" 3개 사유 중 ①이 무효화됐다.** RDS는 **Stage 2에 편입**됐고
> 실제로 사용됐다(#339). 이 블록만 읽으면 "RDS는 기각됨"으로 오독하게 되므로 여기 명시한다.
> **07-29에 실제로 그 오독이 발생했다** — 이 역참조가 없어서였다.
> 단, **최종 목표가 in-cluster라는 결론 자체는 유지**된다(사유 ③이 살아남음). 아래 표 참조.

| 환경 | DB | 근거 |
|------|-----|------|
| **Fly (prod, 24/7)** | **Neon** (변경 없음) | 상시 필요 · **$0** · 관리형 백업/PITR/풀링 |
| **EKS (학습, 가동률 ~15%)** | **in-cluster PostgreSQL + 영속 EBS** | 자기완결형 풀스택 · K8s 스토리지 실습 |

- **코드 변경 0** — `application-prod.yml`이 `jdbc-url: jdbc:postgresql://${DB_HOST}/${DB_NAME}` 등
  **100% 환경변수 기반**(실측 확인). 환경변수만 다르게 주입하면 됨(`transport` 플래그와 같은 패턴).
  **Flyway 마이그레이션 12개**가 스키마를 자동 생성 → 시드 불필요.
- **왜 Neon을 못 걷어내나** (걷어내자는 검토 → 기각):
  ①**비용 동기 없음** — Neon은 현재 **$0**, 걷어내도 절감 0원
  ②**가동률 충돌** — 650h/4,380h ≈ **15%**. prod DB가 클러스터 안이면 **85% 시간 앱이 죽는다**
  ③**역설: in-cluster가 최고가** — 상시로 돌리려면 컨트롤플레인 $73이 따라붙어 **월 $125+**
  (Neon 무료 $0 · Supabase $0 · RDS $12~15 · Neon 유료 ~$19 **< in-cluster 상시 $125+**)
  ④관리형에서 얻던 **자동 백업·PITR·scale-to-zero·PgBouncer·HA**를 전부 자작해야 하고,
  그것들이 클러스터와 함께 85% 시간 죽는다 ⑤노드가 **스팟** — 회수 시 DB 파드 다운
- **왜 RDS가 아니라 in-cluster인가** (RDS 재탈락) — **⚠️ 3개 중 1개는 07-28에 무효화됨**:

  | # | 원래 사유 | 07-28 재판정 |
  |:-:|---|---|
  | ① | RDS는 **클러스터를 꺼도 상시 과금** → 15%만 쓰고 100% 지불, 6개월 $72~90 = 크레딧의 36~45% | 🔴 **무효.** 클러스터와 함께 destroy하면 성립하지 않는다. 원 기각안이 "RDS 상시 가동"만 상정했던 것 |
  | ② | "클러스터 밖 관리형 Postgres"는 **Neon이 이미 그거고 공짜**다 | 🟡 **절반만 유효.** prod DB 대체 목적엔 맞으나 **학습 목적엔 틀렸다** — Neon은 AWS를 안 가르친다 |
  | ③ | **배우려는 걸 안 가르친다** — RDS는 클러스터 밖이라 **EBS·PVC를 전혀 안 건드림.** README 학습 목표(StatefulSet·PVC·EBS)와 불일치 | 🔴 **여전히 유효** — 그래서 최종 목표는 여전히 in-cluster다 |

  → **결론(07-28)**: RDS는 in-cluster의 **대체가 아니라 Stage 2를 완성시키는 임시 조각**이다.
  Stage 3에서 in-cluster로 스왑하는 것은 계획 이탈이 아니라 **이 결정으로 복귀**하는 것.
  단 **`rds.tf`를 삭제하지는 않는다** — 지우면 튜토리얼 Stage 2가 재현 불가가 된다(변수 토글로 처리).
- **학습 워크로드로 Redis보다 Postgres가 낫다** (초안의 Redis 제안 **철회**): 캐시는 유실돼도 안 아파서
  `reclaimPolicy`를 대충 넘기게 된다. **긴장감이 학습을 만든다.** + 앱 연결에 코드 변경이 0이고
  실제 스키마(Flyway 12개)가 돈다.
- ⚠️ **"in-cluster Postgres는 설계와 모순"이라던 초기 경고는 *Neon 대체* 경우에만 유효**했다.
  **병행은 표준 패턴**(테스트 환경)이며 모순이 아니다.
- **부수 효과**: EKS 클러스터가 **외부 의존 0의 자기완결 스택**이 되어 NetworkPolicy·서비스간 통신
  실습이 깨끗해진다(외부 Neon egress 예외 처리 불필요).
- **Neon을 실제로 걷어낼 트리거**: 무료 한도(storage·compute 시간·연결수) 부족. 그때 후보는
  **Neon 유료 · Supabase · RDS**. **in-cluster는 그때도 답이 아니다**(상시 $125+).
  별건: **Neon cold start**(앱 cold start 2~3분의 한 원인)는 DB 이전이 아니라
  `min_machines_running`·lazy-init·PgBouncer로 푼다 — 백로그의 "Spring 시작 시간 최적화" 항목.
