# EKS 2-cluster 구현 계획

> **For agentic workers:** 이 계획은 **인라인 실행**(superpowers:executing-plans) 전제다. `tofu apply`가
> 실제 과금·비가역이라 서브에이전트에 위임하지 않는다. 스텝은 체크박스(`- [ ]`)로 추적.

**Goal:** `infra/aws-eks/2-cluster/`에 핸드롤 OpenTofu로 EKS 컨트롤플레인 + 관리형 노드그룹을 세워 `kubectl get nodes`에서 노드 `Ready`를 확인하고, `tofu destroy` 왕복까지 체득한다.

**Architecture:** 레이어별 state 분리. 2-cluster는 1-network를 `terraform_remote_state`로 읽어 VPC·서브넷을 참조한다. IAM 역할 2개(클러스터/노드) → 클러스터 + OIDC → 노드그룹 → 애드온 3종 → Access Entry 순 의존. public 엔드포인트 + Access Entry API 인증. 로컬 apply/destroy.

**Tech Stack:** OpenTofu(핸드롤, 모듈 없음), AWS provider ~> 5.100, tls provider ~> 4.0, S3 remote backend, tfsec.

## Global Constraints

- **OpenTofu**(`tofu`) 사용 — `terraform` CLI 아님. region `ap-northeast-2`.
- **핸드롤** — `terraform-aws-modules/*` 금지. `aws_eks_*` 직접.
- **public repo**: `.gitignore`를 레이어별로 먼저 둔다. `*.tfstate*`·`*.tfvars`·`.terraform/` 커밋 금지.
- **하드코딩 금지**: account ID·ARN은 data 소스(`aws_caller_identity`)·remote state로. 리터럴 account ID 금지.
- **tfsec 게이트**: 의도적 위반은 근거 주석 `#tfsec:ignore:<rule>`로만 통과.
- **네이밍**: 클러스터 `devquest-eks`(1-network 태그 `kubernetes.io/cluster/devquest-eks`와 일치). 리소스명 `${var.cluster_name}-*`.
- **state key**: `2-cluster/terraform.tfstate`, 버킷 `devquest-eks-tfstate-seoul`, 락 `devquest-eks-tflock`.
- **K8s 버전**: `variables.tf` default에 핀. **apply 직전 `aws eks describe-cluster-versions`(또는 콘솔)로 표준 지원 최신 재확인** 후 갱신.
- **검증 루프**: 유닛테스트 없음(IaC). 각 태스크 = 파일 작성 → `tofu fmt` → `tofu validate`(init `-backend=false`) → 해당 시 `tfsec` → 커밋. 최종 `tofu plan`은 별도 마일스톤.

---

### Task 1: 레이어 스캐폴드 (backend·provider·변수·remote state)

**Files:**
- Create: `infra/aws-eks/2-cluster/.gitignore`
- Create: `infra/aws-eks/2-cluster/versions.tf`
- Create: `infra/aws-eks/2-cluster/providers.tf`
- Create: `infra/aws-eks/2-cluster/backend.tf`
- Create: `infra/aws-eks/2-cluster/variables.tf`
- Create: `infra/aws-eks/2-cluster/remote-state.tf`

**Interfaces:**
- Produces: `var.region`, `var.cluster_name`, `var.kubernetes_version`, `var.node_instance_type`, `var.node_desired_size`, `var.node_min_size`, `var.node_max_size`; `data.terraform_remote_state.network.outputs.{vpc_id,public_subnet_ids,vpc_cidr}`.

**개념 노트:** `terraform_remote_state`는 다른 레이어가 S3에 써둔 output을 읽는 data 소스다. 이게 레이어를 물리적으로 분리하면서(다른 state 키) 값만 넘겨받는 방법. `-backend=false` init은 S3 연결 없이 프로바이더만 받아 문법 검증할 때 쓴다.

- [ ] **Step 1: `.gitignore` 생성** (0-bootstrap과 동일)

```gitignore
# OpenTofu / Terraform — ⚠️ public repo: state·tfvars 절대 커밋 금지
*.tfstate
*.tfstate.*
*.tfstate.backup
.terraform/
.terraform.tfstate.lock.info

# 변수 파일에 이메일·계정ID 등 민감정보 → 커밋 금지 (example만 허용)
*.tfvars
!*.tfvars.example

# 기타
crash.log
crash.*.log
override.tf
override.tf.json
*_override.tf
*_override.tf.json
```

- [ ] **Step 2: `versions.tf` 생성**

```hcl
terraform {
  required_version = ">= 1.8.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.100"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}
```

- [ ] **Step 3: `providers.tf` 생성**

```hcl
provider "aws" {
  region = var.region
}
```

- [ ] **Step 4: `backend.tf` 생성**

```hcl
# 0-bootstrap이 만든 S3 버킷/DynamoDB 락. key에 레이어명 prefix로 격리.
terraform {
  backend "s3" {
    bucket         = "devquest-eks-tfstate-seoul"
    key            = "2-cluster/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "devquest-eks-tflock"
    encrypt        = true
  }
}
```

- [ ] **Step 5: `variables.tf` 생성**

```hcl
variable "region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "cluster_name" {
  description = "EKS 클러스터명 (1-network 서브넷 태그와 일치해야 함)"
  type        = string
  default     = "devquest-eks"
}

variable "kubernetes_version" {
  description = "EKS K8s 버전 (apply 직전 표준 지원 최신으로 재확인)"
  type        = string
  default     = "1.32"
}

variable "node_instance_type" {
  description = "노드 인스턴스 타입 (ARM Graviton)"
  type        = string
  default     = "t4g.small"
}

variable "node_desired_size" {
  description = "노드그룹 희망 노드 수"
  type        = number
  default     = 1
}

variable "node_min_size" {
  description = "노드그룹 최소 노드 수"
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "노드그룹 최대 노드 수"
  type        = number
  default     = 2
}
```

- [ ] **Step 6: `remote-state.tf` 생성**

```hcl
# 1-network 레이어가 S3에 써둔 state를 읽어 VPC·서브넷을 참조한다.
data "terraform_remote_state" "network" {
  backend = "s3"
  config = {
    bucket = "devquest-eks-tfstate-seoul"
    key    = "1-network/terraform.tfstate"
    region = var.region
  }
}
```

- [ ] **Step 7: 검증**

```bash
cd infra/aws-eks/2-cluster
tofu fmt
tofu init -backend=false
tofu validate
```
Expected: `tofu validate` → `Success! The configuration is valid.`

- [ ] **Step 8: 커밋**

```bash
git add infra/aws-eks/2-cluster/
git commit -m "chore(infra): EKS 2-cluster 스캐폴드 — backend·provider·변수·remote state"
```

---

### Task 2: IAM 역할 (① 클러스터 역할 + ④ 노드 역할)

**Files:**
- Create: `infra/aws-eks/2-cluster/iam.tf`

**Interfaces:**
- Consumes: `var.cluster_name`.
- Produces: `aws_iam_role.cluster` (arn: `aws_iam_role.cluster.arn`), `aws_iam_role.node` (arn: `aws_iam_role.node.arn`), 정책 첨부 4건.

**개념 노트:** 역할이 2개인 이유 = **컨트롤플레인 ≠ 데이터플레인**. 클러스터 역할은 `eks.amazonaws.com`이 assume해 컨트롤플레인이 네 계정에서 ENI 등을 만들게 한다. 노드 역할은 `ec2.amazonaws.com`이 assume해 EC2 노드가 클러스터 조인(`WorkerNodePolicy`)·파드 ENI(`CNI_Policy`)·이미지 풀(`ECRReadOnly`)을 하게 한다. 관리형 정책 ARN은 AWS 소유(`arn:aws:iam::aws:policy/...`)라 하드코딩 예외(계정 무관 고정 값).

- [ ] **Step 1: `iam.tf` 생성**

```hcl
# ── ① 클러스터 역할 ────────────────────────────────────────────
data "aws_iam_policy_document" "cluster_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "cluster" {
  name               = "${var.cluster_name}-cluster-role"
  assume_role_policy = data.aws_iam_policy_document.cluster_assume.json
}

resource "aws_iam_role_policy_attachment" "cluster_eks" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

# ── ④ 노드 역할 ────────────────────────────────────────────────
data "aws_iam_policy_document" "node_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "node" {
  name               = "${var.cluster_name}-node-role"
  assume_role_policy = data.aws_iam_policy_document.node_assume.json
}

resource "aws_iam_role_policy_attachment" "node_worker" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "node_cni" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "node_ecr" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}
```

- [ ] **Step 2: 검증**

```bash
cd infra/aws-eks/2-cluster
tofu fmt
tofu validate
tfsec .
```
Expected: `validate` Success. `tfsec` → IAM 관련 No problems (관리형 정책 첨부만이라 와일드카드 경고 없음).

- [ ] **Step 3: 커밋**

```bash
git add infra/aws-eks/2-cluster/iam.tf
git commit -m "feat(infra): EKS 2-cluster IAM — 클러스터·노드 역할"
```

---

### Task 3: 클러스터 + OIDC (② aws_eks_cluster + ③ OIDC 프로바이더)

**Files:**
- Create: `infra/aws-eks/2-cluster/cluster.tf`

**Interfaces:**
- Consumes: `aws_iam_role.cluster.arn`, `aws_iam_role_policy_attachment.cluster_eks`, `data.terraform_remote_state.network.outputs.public_subnet_ids`, `var.cluster_name`, `var.kubernetes_version`.
- Produces: `aws_eks_cluster.main` (name/endpoint/certificate_authority/identity), `aws_iam_openid_connect_provider.oidc` (arn).

**개념 노트:** `access_config`의 `authentication_mode = "API"`는 레거시 `aws-auth` configmap 대신 **신형 Access Entry API**만 쓰겠다는 선언. `bootstrap_cluster_creator_admin_permissions = false`로 두는 이유 = Task 6에서 **명시적 Access Entry를 직접 만들어 배우기 위해**(자동 부여를 켜면 같은 principal에 중복 엔트리 충돌). ③ OIDC 프로바이더는 클러스터의 OIDC issuer를 IAM에 등록해 **IRSA**(파드가 IAM 역할 assume) 토대를 깐다 — 이번 Stage엔 안 쓰지만 지금 만들어두면 이후 Stage가 바로 사용. `depends_on`으로 정책 첨부가 끝난 뒤 클러스터가 생성되게 순서 고정.

- [ ] **Step 1: `cluster.tf` 생성**

```hcl
# ── ② EKS 컨트롤플레인 ─────────────────────────────────────────
# tfsec 근거 ignore:
#   public 엔드포인트 개방은 단독 데스크톱 학습 놀이터의 의도된 선택
#   (인증은 IAM + Access Entry로 게이트). prod면 CIDR 제한/private.
#   control-plane 로깅·secret KMS 암호화는 CloudWatch·KMS 비용 회피로 생략.
#tfsec:ignore:aws-eks-no-public-cluster-access
#tfsec:ignore:aws-eks-no-public-cluster-access-to-cidr
#tfsec:ignore:aws-eks-enable-control-plane-logging
#tfsec:ignore:aws-eks-encrypt-secrets
resource "aws_eks_cluster" "main" {
  name     = var.cluster_name
  version  = var.kubernetes_version
  role_arn = aws_iam_role.cluster.arn

  vpc_config {
    subnet_ids              = data.terraform_remote_state.network.outputs.public_subnet_ids
    endpoint_public_access  = true
    endpoint_private_access = false
    public_access_cidrs     = ["0.0.0.0/0"]
  }

  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = false
  }

  depends_on = [aws_iam_role_policy_attachment.cluster_eks]
}

# ── ③ OIDC 프로바이더 (IRSA 토대) ──────────────────────────────
data "tls_certificate" "oidc" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "oidc" {
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.oidc.certificates[0].sha1_fingerprint]
}
```

- [ ] **Step 2: 검증**

```bash
cd infra/aws-eks/2-cluster
tofu fmt
tofu validate
tfsec .
```
Expected: `validate` Success. `tfsec` → No problems detected (위 4개 ignore가 근거 주석으로 통과).

- [ ] **Step 3: 커밋**

```bash
git add infra/aws-eks/2-cluster/cluster.tf
git commit -m "feat(infra): EKS 2-cluster 컨트롤플레인 + OIDC 프로바이더"
```

---

### Task 4: 노드그룹 (⑤ aws_eks_node_group)

**Files:**
- Create: `infra/aws-eks/2-cluster/nodes.tf`

**Interfaces:**
- Consumes: `aws_eks_cluster.main.name`, `aws_iam_role.node.arn`, `data.terraform_remote_state.network.outputs.public_subnet_ids`, `var.cluster_name`, `var.node_instance_type`, `var.node_desired_size/min_size/max_size`, 노드 정책 첨부 3건.
- Produces: `aws_eks_node_group.main`.

**개념 노트:** 관리형 노드그룹은 AWS가 EC2 오토스케일링 그룹·수명주기를 대신 관리한다. `ami_type = "AL2023_ARM_64_STANDARD"`는 t4g(Graviton ARM64)용 EKS 최적화 AMI. `capacity_type = "SPOT"`은 남는 용량을 싸게 쓰되 중단 감내(학습용이라 OK). `depends_on`으로 노드 역할 정책이 먼저 붙어야 노드가 조인 가능.

- [ ] **Step 1: `nodes.tf` 생성**

```hcl
# ── ⑤ 관리형 노드그룹 (t4g.small Spot) ────────────────────────
resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-ng"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = data.terraform_remote_state.network.outputs.public_subnet_ids

  ami_type       = "AL2023_ARM_64_STANDARD"
  instance_types = [var.node_instance_type]
  capacity_type  = "SPOT"

  scaling_config {
    desired_size = var.node_desired_size
    min_size     = var.node_min_size
    max_size     = var.node_max_size
  }

  update_config {
    max_unavailable = 1
  }

  depends_on = [
    aws_iam_role_policy_attachment.node_worker,
    aws_iam_role_policy_attachment.node_cni,
    aws_iam_role_policy_attachment.node_ecr,
  ]
}
```

- [ ] **Step 2: 검증**

```bash
cd infra/aws-eks/2-cluster
tofu fmt
tofu validate
tfsec .
```
Expected: `validate` Success. `tfsec` No new problems.

- [ ] **Step 3: 커밋**

```bash
git add infra/aws-eks/2-cluster/nodes.tf
git commit -m "feat(infra): EKS 2-cluster 노드그룹 — t4g.small Spot"
```

---

### Task 5: 애드온 (⑥ aws_eks_addon ×3)

**Files:**
- Create: `infra/aws-eks/2-cluster/addons.tf`

**Interfaces:**
- Consumes: `aws_eks_cluster.main.name`, `aws_eks_node_group.main`.
- Produces: `aws_eks_addon.vpc_cni`, `aws_eks_addon.kube_proxy`, `aws_eks_addon.coredns`.

**개념 노트:** 이 3종이 없으면 노드가 `Ready`가 안 된다. `vpc-cni`=파드에 VPC IP 부여, `kube-proxy`=서비스 → 파드 라우팅, `coredns`=클러스터 내부 DNS. 애드온 버전을 명시 안 하면 클러스터 버전에 맞는 기본 버전이 설치된다. `coredns`는 스케줄될 노드가 있어야 활성화되므로 노드그룹에 `depends_on`.

- [ ] **Step 1: `addons.tf` 생성**

```hcl
# ── ⑥ 관리형 애드온 (노드 Ready 필수 3종) ─────────────────────
resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "vpc-cni"
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "kube-proxy"
}

resource "aws_eks_addon" "coredns" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "coredns"

  # coredns 파드가 스케줄되려면 노드가 있어야 함
  depends_on = [aws_eks_node_group.main]
}
```

- [ ] **Step 2: 검증**

```bash
cd infra/aws-eks/2-cluster
tofu fmt
tofu validate
```
Expected: `validate` Success.

- [ ] **Step 3: 커밋**

```bash
git add infra/aws-eks/2-cluster/addons.tf
git commit -m "feat(infra): EKS 2-cluster 애드온 — vpc-cni·kube-proxy·coredns"
```

---

### Task 6: 접근 관리 + 출력 (⑦ Access Entry + outputs)

**Files:**
- Create: `infra/aws-eks/2-cluster/access.tf`
- Create: `infra/aws-eks/2-cluster/outputs.tf`

**Interfaces:**
- Consumes: `aws_eks_cluster.main.name`, `aws_iam_openid_connect_provider.oidc.arn`.
- Produces: `aws_eks_access_entry.admin`, `aws_eks_access_policy_association.admin`; outputs `cluster_name`, `cluster_endpoint`, `cluster_ca`(sensitive), `oidc_provider_arn`.

**개념 노트:** Access Entry는 IAM 신원을 K8s RBAC에 연결하는 신형 API. `data.aws_caller_identity.current.arn`은 지금 apply를 실행하는 신원(로컬 `bootstrap-admin` IAM 사용자)의 ARN. 여기에 `AmazonEKSClusterAdminPolicy`를 cluster 스코프로 붙이면 그 사용자가 kubectl admin이 된다. ⚠️ **전제**: 로컬 실행 신원이 IAM 사용자여야 함(assume-role 세션 ARN은 access entry principal로 부적합). CI/역할 기반으로 바뀌면 `principal_arn`을 역할 ARN 변수로 교체.

- [ ] **Step 1: `access.tf` 생성**

```hcl
# ── ⑦ Access Entry — bootstrap-admin을 클러스터 admin으로 ──────
data "aws_caller_identity" "current" {}

resource "aws_eks_access_entry" "admin" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = data.aws_caller_identity.current.arn
  type          = "STANDARD"
}

resource "aws_eks_access_policy_association" "admin" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = data.aws_caller_identity.current.arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.admin]
}
```

- [ ] **Step 2: `outputs.tf` 생성**

```hcl
output "cluster_name" {
  description = "EKS 클러스터명 (kubeconfig 갱신용)"
  value       = aws_eks_cluster.main.name
}

output "cluster_endpoint" {
  description = "클러스터 API 엔드포인트"
  value       = aws_eks_cluster.main.endpoint
}

output "cluster_ca" {
  description = "클러스터 CA 인증서 (base64)"
  value       = aws_eks_cluster.main.certificate_authority[0].data
  sensitive   = true
}

output "oidc_provider_arn" {
  description = "IRSA용 OIDC 프로바이더 ARN (이후 Stage에서 사용)"
  value       = aws_iam_openid_connect_provider.oidc.arn
}
```

- [ ] **Step 3: 검증**

```bash
cd infra/aws-eks/2-cluster
tofu fmt
tofu validate
tfsec .
```
Expected: `validate` Success. `tfsec` No problems detected (전체).

- [ ] **Step 4: 커밋**

```bash
git add infra/aws-eks/2-cluster/access.tf infra/aws-eks/2-cluster/outputs.tf
git commit -m "feat(infra): EKS 2-cluster Access Entry + outputs"
```

---

### Task 7: 전체 plan 마일스톤 (과금 없음 — 검증만)

**Files:** (없음 — 실행·검증 마일스톤)

**개념 노트:** 여기서 처음으로 **실제 backend에 init**해 전체 config를 plan한다. `plan`은 **아무것도 만들지 않는다**(과금 0) — 무엇이 생길지만 보여준다. 이 출력을 리소스별로 해설하고 비용 영향을 확인하는 게 CLAUDE.md의 apply-전 게이트다.

- [ ] **Step 1: K8s 버전 재확인**

```bash
aws eks describe-cluster-versions --region ap-northeast-2 \
  --query 'clusterVersions[?clusterVersionStatus==`STANDARD_SUPPORT`].clusterVersion' --output text
```
표준 지원 최신이 `variables.tf` default와 다르면 갱신 후 커밋.

- [ ] **Step 2: 실제 backend로 init**

```bash
cd infra/aws-eks/2-cluster
tofu init
```
Expected: `Successfully configured the backend "s3"` + 프로바이더 설치 완료.

- [ ] **Step 3: 전체 검증 + 스캔**

```bash
tofu validate
tfsec .
```
Expected: validate Success, tfsec No problems detected.

- [ ] **Step 4: plan (과금 없음)**

```bash
tofu plan
```
Expected: `Plan: N to add, 0 to change, 0 to destroy` (N ≈ 14: 역할2·정책첨부4·클러스터1·OIDC1·노드그룹1·애드온3·엔트리1·연결1). 에러 없이 계획 산출.

- [ ] **Step 5: plan 해설 + 비용 게이트 (사용자 확인)**

plan 출력의 각 리소스를 해설하고 비용 영향(② $0.10/hr, ⑤ Spot+EBS, 나머지 $0)을 제시한다. **사용자가 명시적으로 승인하기 전까지 Task 8로 넘어가지 않는다.**

- [ ] **Step 6: (버전 갱신했으면) 커밋**

```bash
git add infra/aws-eks/2-cluster/variables.tf
git commit -m "chore(infra): 2-cluster K8s 버전 표준 지원 최신으로 핀"
```

---

### Task 8: ★ apply + 검증 + teardown 왕복 (과금 — 사용자 게이트 후에만)

**Files:** (없음 — 운영 마일스톤. 서브에이전트 위임 금지.)

> ⚠️ **이 태스크는 실제 과금·비가역이다.** Task 7 Step 5의 사용자 승인 없이는 시작하지 않는다.
> apply·destroy 시각을 `docs/eks-migration-log.md`에 실시간 기록한다.

- [ ] **Step 1: apply (★ 과금 시작)**

```bash
cd infra/aws-eks/2-cluster
tofu apply
```
Expected: `Apply complete!` (~15분, 컨트롤플레인 프로비저닝이 대부분). 일지에 apply 시각 append.

- [ ] **Step 2: kubeconfig 갱신**

```bash
aws eks update-kubeconfig --name devquest-eks --region ap-northeast-2
```
Expected: `Updated context ... in ~/.kube/config`.

- [ ] **Step 3: 검증 — 노드 Ready**

```bash
kubectl get nodes
kubectl get pods -A
```
Expected: 노드 1개 `STATUS=Ready`. `kube-system`에 coredns·kube-proxy·aws-node(vpc-cni) 파드 `Running`.

- [ ] **Step 4: 성공 기록**

`docs/eks-tutorial-steps.md`에 성공한 명령어만 정답 경로로(개념 설명 포함) 추가. `docs/eks-migration-log.md`에 실측(생성 시간·막힌 점·해결) append. 휘발성 화면 필요 시 사용자에게 "지금 캡처" 알림.

- [ ] **Step 5: teardown 왕복**

```bash
tofu destroy
tofu state list
```
Expected: `Destroy complete!` (~10분). `state list` empty. 일지에 destroy 시각·총 과금 시간 append.

- [ ] **Step 6: 잔존물 육안 확인**

```bash
aws ec2 describe-instances --region ap-northeast-2 \
  --filters "Name=tag:eks:cluster-name,Values=devquest-eks" \
  --query 'Reservations[].Instances[].State.Name'
aws eks list-clusters --region ap-northeast-2
```
Expected: 인스턴스 없음(빈 배열/terminated), 클러스터 목록에 devquest-eks 없음. (이번 범위는 Ingress/PVC 없어 잔존물 없음.)

---

## 실행 후 (CONTEXT·일지 갱신)

- `.claude/CONTEXT.md`: 2-cluster 완료 반영, "다음 작업" → gitops 또는 Stage 1(ECR·앱 배포).
- PR 생성(코드는 IaC 파일들 — state·tfvars 제외). CI matrix에 2-cluster는 **추가하지 않음**(로컬 관리 결정).

## 범위 밖 (이후 Stage)
IRSA 실사용(Stage 2) / EBS CSI + StatefulSet(Stage 3) / ALB Ingress(Stage 4) / ECR(Stage 1) / ArgoCD(Stage 5).
이 Stage들부터 teardown이 커진다(`kubectl delete ingress,pvc --all -A`를 destroy 전에).
