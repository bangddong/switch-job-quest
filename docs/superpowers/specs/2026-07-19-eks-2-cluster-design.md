# EKS 2-cluster 레이어 설계

> 2026-07-19 brainstorming 산출물. IaC-first EKS 학습 놀이터의 `2-cluster` 레이어.
> 계획 상위 문서: `infra/aws-eks/README.md` / 진행 상태: `.claude/CONTEXT.md` / 일지: `docs/eks-migration-log.md`.

## 목적

`0-bootstrap`(remote backend·OIDC·IAM·예산)과 `1-network`(VPC·퍼블릭 서브넷·IGW)에 이어,
**EKS 컨트롤플레인 + 관리형 노드그룹**을 세워 `kubectl get nodes`에서 `Ready`를 확인하는 것까지가 이 레이어의 완료 조건.

이 레이어는 **세션 수명(destroy-after-use)** 자원이다. 실습 시작 시 `apply`, 자리 뜰 때 `destroy`.
컨트롤플레인이 유휴에도 $0.10/hr 과금되는 유일한 상시 과금 지점이라, 부수고 다시 만드는 규율이 핵심.

## 확정된 설계 결정 (brainstorming)

| 결정 | 선택 | 사유 |
|------|------|------|
| 모듈 vs 핸드롤 | **핸드롤** (`aws_eks_cluster` 등 직접) | 학습 트랙 — 리소스 하나하나가 눈에 보여야 함. 0/1 레이어와 일관 |
| 첫 apply 범위 | **최소 커널+노드** | "한 번에 하나씩" 원칙. `Ready`까지만. IRSA·EBS·ECR·ALB는 각 Stage에서 추가 |
| API 엔드포인트 | **Public 오픈** | 단독 데스크톱·destroy-after-use. IAM+Access Entry로 인증 게이트라 안전. 집 동적 IP 변경에도 안 끊김 |
| apply/destroy 실행 | **로컬** (CI 아님) | destroy 왕복이 잦은 세션 자원. `tofu destroy` 한 줄이 CI 머지 왕복보다 빠르고 비용 통제가 손에 잡힘 |
| 접근 관리 | **Access Entry API** | 신형. `aws-auth` configmap(레거시) 대신 `authentication_mode = API` |
| 노드 | **t4g.small Spot ×1** (min 1 / max 2) | ARM Graviton 저렴, 학습용이라 Spot 중단 감내. 단독 실습엔 1노드 충분 |
| K8s 버전 | **최신 표준 지원 버전 핀** | apply 직전 `aws eks describe-addon-versions` 등으로 현재 표준 지원 버전 재확인 후 `variables.tf`에 고정 |

### 기각한 대안
- **terraform-aws-modules/eks 모듈**: 실무 표준이나 내부가 추상화 뒤에 숨어 "무엇을 왜"를 덜 배움. 재현 검증 단계에서 같은 걸 모듈로 다시 짜 비교하는 건 향후 선택지로 열어둠.
- **Public + 내 IP CIDR 제한**: 더 안전하나 가정용 동적 IP 변경 시 kubectl 끊김 → allowlist 재등록 번거로움. 놀이터 수명(수 시간)엔 과함.
- **CI apply-on-merge**(0/1 레이어 방식): 상주 레이어엔 맞으나 세션 자원엔 destroy 왕복이 굼뜸.

## 리소스 구성 (빌드 순서 = 의존 순서)

`infra/aws-eks/2-cluster/`에 핸드롤로 배치. 번호는 생성 순서(destroy는 역순).

| # | 리소스 | 파일 | 하는 일 / 학습 포인트 | 비용 |
|---|--------|------|----------------------|:----:|
| ① | `aws_iam_role` 클러스터 역할 + `AmazonEKSClusterPolicy` | iam.tf | 컨트롤플레인이 AWS API(ENI 등) 호출할 신원. 신뢰 주체 `eks.amazonaws.com` | $0 |
| ② | `aws_eks_cluster` | cluster.tf | 컨트롤플레인 본체. `vpc_config`에 1-network 서브넷·public 엔드포인트. **여기서 $0.10/hr 시작** | $0.10/hr |
| ③ | `aws_iam_openid_connect_provider` | cluster.tf | 클러스터 OIDC를 IAM에 등록 = **IRSA 토대**. 지금 깔아두면 이후 Stage에서 파드가 IAM 역할 사용 가능 | $0 |
| ④ | `aws_iam_role` 노드 역할 + 3정책 (`AmazonEKSWorkerNodePolicy`·`AmazonEKS_CNI_Policy`·`AmazonEC2ContainerRegistryReadOnly`) | iam.tf | EC2 노드가 클러스터 조인·ENI·이미지풀 할 신원. **역할이 2개인 이유 = 컨트롤플레인 ≠ 데이터플레인, 권한 분리** | $0 |
| ⑤ | `aws_eks_node_group` | nodes.tf | 관리형 노드그룹 t4g.small Spot ×1. AWS가 EC2 수명주기 관리. `desired=1, min=1, max=2` | Spot~$0.007/hr + EBS |
| ⑥ | `aws_eks_addon` ×3 (vpc-cni·kube-proxy·coredns) | addons.tf | 파드 네트워킹·서비스 프록시·클러스터 DNS. **없으면 노드가 `Ready` 안 됨.** 관리형 애드온으로 버전 관리 | $0 |
| ⑦ | `aws_eks_access_entry` + `aws_eks_access_policy_association` | access.tf | `bootstrap-admin`을 클러스터 admin으로 (`AmazonEKSClusterAdminPolicy`). **신형 Access Entry API.** kubectl 되게 하는 마지막 배선 | $0 |

과금 지점은 **②(컨트롤플레인)·⑤(노드+EBS)** 둘뿐. 나머지는 전부 $0.

## state 배선

### 레이어 간 연결 — `terraform_remote_state`
2-cluster는 VPC를 만들지 않고 1-network가 S3에 써둔 state를 데이터소스로 읽는다.

```hcl
data "terraform_remote_state" "network" {
  backend = "s3"
  config = {
    bucket = "devquest-eks-tfstate-seoul"
    key    = "1-network/terraform.tfstate"
    region = "ap-northeast-2"
  }
}
# 사용: data.terraform_remote_state.network.outputs.public_subnet_ids / vpc_id
```

1-network가 이미 노출하는 outputs: `vpc_id`, `public_subnet_ids`, `vpc_cidr`.

### state 격리
| 레이어 | S3 key | 버킷 | 락 테이블 |
|--------|--------|------|-----------|
| 0-bootstrap | `0-bootstrap/terraform.tfstate` | devquest-eks-tfstate-seoul | devquest-eks-tflock |
| 1-network | `1-network/terraform.tfstate` | 〃 | 〃 |
| **2-cluster** | **`2-cluster/terraform.tfstate`** | 〃 | 〃 |

다른 키 = 다른 파일 → 2-cluster `destroy`가 1-network state를 물리적으로 건드릴 수 없음.

## 파일 구조

```
infra/aws-eks/2-cluster/
  .gitignore      # *.tfstate*·*.tfvars·.terraform/ (0-bootstrap·1-network과 동일 — 레이어별 보유)
  backend.tf      # S3 backend (key = 2-cluster/terraform.tfstate)
  providers.tf    # aws provider (region ap-northeast-2)
  versions.tf     # tofu·aws provider 버전 핀
  remote-state.tf # 1-network 읽는 terraform_remote_state data
  iam.tf          # ① 클러스터 역할 + ④ 노드 역할 (+ 정책 첨부)
  cluster.tf      # ② aws_eks_cluster + ③ OIDC 프로바이더
  nodes.tf        # ⑤ aws_eks_node_group
  addons.tf       # ⑥ aws_eks_addon ×3
  access.tf       # ⑦ access_entry + policy association
  outputs.tf      # cluster 이름·엔드포인트·CA 데이터 (kubeconfig 갱신용)
  variables.tf    # 클러스터명·K8s 버전·인스턴스 타입·노드 스케일 등
```

⚠️ `.gitignore`는 **레이어별**로 둔다(0-bootstrap·1-network 각자 보유 — 루트에 없음). 2-cluster도 동일 내용의 `.gitignore`를 먼저 생성해 `*.tfstate*`·`*.tfvars`·`.terraform/`를 제외한다(public repo — state에 평문 secret 유입 방지).

## 워크플로 (로컬)

```bash
cd infra/aws-eks/2-cluster
tofu init            # backend 연결 + 프로바이더 (1회)
tofu plan            # 리소스 하나씩 해설 + 비용 게이트 (사용자 확인 필수)
tofu apply           # ★ 과금 시작 (~15분: 컨트롤플레인 프로비저닝)
aws eks update-kubeconfig --name devquest-eks --region ap-northeast-2
kubectl get nodes    # 검증: 노드 Ready = 성공
```

## Teardown

이번 범위(최소 커널+노드)는 워크로드(Ingress/PVC)가 없어 단순:

```bash
tofu destroy         # ~10분. ②⑤ 회수 → 과금 정지
tofu state list      # empty 확인
```

> ⚠️ **Stage가 진행되면 teardown 절차가 커진다.** Stage 3(EBS PVC)·Stage 4(ALB Ingress) 이후로는
> `kubectl delete ingress,pvc --all -A`를 `tofu destroy` **전에** 먼저 실행해야 한다 — AWS LB Controller가
> 만든 ALB와 PVC의 EBS는 tofu state 밖이라 destroy가 못 지우고 과금이 샌다(README 157줄). **이번 레이어엔 해당 없음.**

## 비용 수명주기

| 상태 | 시간당 | 하루 방치 | 비고 |
|------|--------|-----------|------|
| apply 후 실습 중 | ~$0.11/hr | — | ② $0.10 + ⑤ Spot ~$0.007 + EBS |
| **destroy 후** | **$0** | **$0** | VPC·backend는 상시 $0라 유지 |
| 안 부수고 방치 | ~$0.11/hr | ~$2.6/일 | 이걸 피하려고 매 세션 destroy |

가격은 서울(ap-northeast-2) 근사치. apply 직전 재확인. 매 세션 apply·destroy 시각을 `docs/eks-migration-log.md`에 기록해 실제 과금 시간 추적(블로그 소스).

## 완료 조건

1. `tofu apply` 성공 (7 리소스 생성)
2. `kubectl get nodes` → 노드 `Ready`
3. `tofu destroy` → `tofu state list` empty (teardown 왕복 1회 체득)
4. `docs/eks-tutorial-steps.md`에 성공 확인된 명령어만 정답 경로로 기록 (개념 설명 포함)
5. `docs/eks-migration-log.md`에 결정·막힘·해결·비용 실시간 기록

## 범위 밖 (이후 Stage)
- IRSA 실사용 예제 (Stage 2)
- EBS CSI 드라이버 + StorageClass + Postgres StatefulSet (Stage 3)
- AWS Load Balancer Controller + ALB Ingress (Stage 4)
- ECR 리포지토리 + 앱 이미지 푸시 (Stage 1)
- metrics-server·HPA·Karpenter·ArgoCD (Stage 5)
