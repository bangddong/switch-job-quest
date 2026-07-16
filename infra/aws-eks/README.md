# AWS EKS 학습 놀이터 — 계획

> 2026-07-13 대화에서 확정된 방향을 문서화. 대화 원본은 세션 트랜스크립트에만 있었고 이 문서가 첫 기록.
> 진행 상태는 아래 "진행 현황" 표를 갱신한다.

## 한 줄 정의

> **AWS EKS를 OpenTofu로 세웠다 부수는 K8s 학습 놀이터.** destroy-after-use + 신규계정 $200 크레딧.
> **프로덕션은 Fly.io($0) 그대로.** 기존 kind 학습 트랙(Stage 1~4)과 합류시킨다.

## ⚠️ 이건 "프로덕션 이관"이 아니다

이 점이 가장 오해하기 쉽다. 프로덕션 이전은 **검토했고 명시적으로 기각**했다:

| | 내용 |
|---|---|
| 검토안 | 블루-그린으로 `api.quest.dhbang.co.kr`를 Fly → AWS ALB로 flip |
| 비용 | ECS Fargate 상시 prod ≈ **월 $35** (ALB $16 고정 + Fargate 1GB $18 + ECR/데이터 $2) |
| 기각 사유 | $200 크레딧 ÷ $35 = **5.7개월** → "6개월 뒤 월 $35 청구서"로 문제를 **없앤 게 아니라 이연**. 이 프로젝트의 출발점이 "$5.7/월 스케일업도 안 쓴다"였는데 그 **6배** |
| 추가 사유 | $200을 상시 prod에 태우면 **띄웠다 부수는 실험에 쓸 크레딧이 소진됨**. prod를 Fly에 두면 같은 $200으로 6개월 내내 실험 가능 |

**결론: prod = Fly($0) 유지. AWS = 학습 전용, 안 쓸 땐 destroy.**

> 나중에 진짜 이관하고 싶어지면 그때 별도 판단. 단 **선행 조건**: 메타스페이스 누수 검증 (`.claude/CONTEXT.md` 참조).
> 누수면 AWS로 옮겨도 따라간다 — 인스턴스만 커져서 죽는 주기가 늘 뿐이다.

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
state는 **로컬로 시작**(놀이터라 충분) → Stage 4에서 S3 백엔드를 학습 모듈로 전환.

---

## 학습 스테이지 (kind 트랙과 1:1 합류)

| Stage | 세울 것 | 새로 배우는 EKS 고유 개념 | kind 대응 |
|:--:|---|---|---|
| **0** | OpenTofu로 VPC + EKS 클러스터 + 관리형 노드그룹(t4g.small Spot ×1~2) + `kubectl` 연결 | 컨트롤플레인, 노드그룹, OIDC | (신규) |
| **1** | Deployment + Service로 앱 배포 (ECR 이미지) | ECR·이미지 풀, 실클러스터 `kubectl` | Stage 1 재사용 |
| **2** | Secret / Config | **IRSA** (IAM Roles for ServiceAccount) — EKS의 정수 | Stage 2 재사용 |
| **3** | **Postgres StatefulSet + EBS CSI + PVC** | StorageClass, 동적 EBS 프로비저닝 | Stage 3 직결 |
| **4** | **AWS Load Balancer Controller → ALB Ingress** | IngressClass, ALB target-type | Stage 4 직결 |
| **5** (선택) | metrics-server·HPA, Karpenter, ArgoCD | 오토스케일, GitOps | (확장) |

### 진행 현황

| Stage | Status |
|:--:|:--:|
| 0 | ⬜ |
| 1~5 | ⬜ |

---

## 리포 구조

```
infra/aws-eks/
  README.md              # 이 문서 — up/down 절차 + 비용 체크리스트
  tofu/                  # 계층 1: 인프라 (별도 state)
    providers.tf  variables.tf  outputs.tf
    vpc.tf  eks.tf  nodegroup.tf  iam-irsa.tf  ecr.tf
    terraform.tfvars     # gitignore (시크릿·계정ID)
    .gitignore           # *.tfstate, *.tfvars
  k8s/                   # 계층 2: 워크로드 (Helm/Kustomize 매니페스트)
    stage1-deploy/  stage2-config/  stage3-postgres/  stage4-ingress/
  docs/
    stage*-learning.md   # kind 트랙(k8s/docs/) 방식 그대로 학습 기록
```

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
- **NAT Gateway 절대 회피** (+$32/mo 폭탄) → **퍼블릭 서브넷 + 노드 공인IP**로 구성
- **ARM64** (t4g / Graviton) — 우리 alpine 이미지 arm64 호환, x86보다 ~20% 저렴
- **Spot 인스턴스** 노드그룹 — 학습용이라 중단 감내 가능
- 세션 종료 = `tofu destroy` → `tofu state list` 비었는지 확인 (위 Teardown 순서 준수)
- 크레딧 소진율 주기 확인. **크레딧 = 해결이 아니라 유예**임을 기억

> ⚠️ 2025-07 이후 신규 AWS 계정은 **Free Plan** 구조 — 크레딧 소진 시 Paid Plan 업그레이드 안 하면
> 리소스가 중단될 수 있다. 정확한 만료 동작은 **가입 시 직접 확인** 필요 (신정책이라 케이스별 상이 🟡).

### 계정 관련 메모

기존 계정 프리티어는 소진됨. **새 이메일로 계정 신설 시 $200/6개월 크레딧** 대상.

> ✅ **07-16 실측 정정**: 계정 신설 완료. 크레딧은 $200 단일이 아니라 **$100 기본 + $20×5 활동**
> 분할 지급이며, 만료는 "6개월"이 아니라 **2027-07-15 (가입 +1년)**. 상세: `docs/eks-migration-log.md`.

> 단 **IaC 학습엔 프리티어가 사실 필요 없다** — "띄우고 → 만지고 → `destroy`" 사이클은 시간당 과금이라
> 기존 계정에서도 세션당 몇 센트~몇 달러다. 크레딧 노린 계정 갈아타기 반복은 ToS 정신에 걸리고,
> destroy 습관이 있으면 실익도 미미. **기존 계정 + 가드레일**도 충분히 합리적.

---

## 착수 순서 (다음 세션)

1. AWS 계정 결정 (기존 + 가드레일 / 신규 + 크레딧) → **Budgets 알림부터 설정**
2. OpenTofu 설치, `infra/aws-eks/tofu/` 스캐폴딩
3. **Stage 0** — VPC + EKS + 노드그룹 + `kubectl` 연결까지 `tofu apply` → **즉시 `destroy` 1회 왕복** (teardown 절차를 먼저 몸에 익힌다)
4. 이후 Stage 1~4 순차
5. **재현 검증 (완료 조건)** — 이관 완료 후 클러스터 전체 destroy → `docs/eks-tutorial-steps.md`만
   보고 처음부터 재현. 막히면 문서 수정 후 재시도, 통과해야 완료 (블로그 공개 전 정확성 검증)

## 참조

| 주제 | 위치 |
|------|------|
| kind 학습 트랙 (Stage 1~4) | `k8s/docs/stage*-learning.md` |
| K8s 학습 로드맵 (전체 10단계) | `E:/development/wiki/tech/k8s/_roadmap.md` |
| 현재 작업 상태·미해결 이슈 | `.claude/CONTEXT.md` |
