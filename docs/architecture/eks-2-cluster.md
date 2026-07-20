# EKS 2-cluster 아키텍처

> AWS EKS 학습 놀이터의 `2-cluster` 레이어 — 컨트롤플레인 + 관리형 노드그룹.
> 이 문서는 **살아있는 다이어그램 소스**다. 레이어/Stage가 바뀌면 여기부터 갱신한다.
> 라이브 렌더(줌·전체화면): 아티팩트 링크는 `.claude/CONTEXT.md` 참조.

`ap-northeast-2` · 핸드롤 OpenTofu · destroy-after-use. **빨간(과금) 노드는 `aws_eks_cluster`와 노드뿐**, 나머지는 $0.

```mermaid
flowchart TB
  dev["kubectl · OpenTofu<br/>bootstrap-admin (IAM user)"]

  subgraph cloud["AWS Cloud · ap-northeast-2"]
    direction TB

    subgraph boot["0-bootstrap · 상시 $0"]
      direction LR
      s3["S3 tfstate"]
      ddb["DynamoDB lock"]
      ghoidc["GitHub OIDC → IAM"]
      bud["Budgets"]
    end

    subgraph c2["2-cluster · 세션 수명"]
      cp["EKS control plane<br/>public endpoint · auth=API"]
      oidc["OIDC provider<br/>(IRSA 토대)"]
      access["Access Entry<br/>→ ClusterAdmin"]
      ng["Managed node group<br/>t4g.small Spot · 1~2"]
      addons["addons<br/>vpc-cni · kube-proxy · coredns"]
    end

    subgraph vpc["VPC 10.0.0.0/16 · 1-network · $0"]
      igw["Internet Gateway"]
      subgraph az1["AZ 2a"]
        sn1["Public subnet /20"]
        n1["EC2 node"]
      end
      subgraph az2["AZ 2c"]
        sn2["Public subnet /20"]
      end
    end
  end

  dev -->|"HTTPS 443"| cp
  dev -.->|"state"| s3
  igw --> sn1
  igw --> sn2
  cp --> ng
  ng --> n1
  n1 --> sn1
  ng -.-> addons
  cp -.-> oidc
  access --> cp

  classDef bill fill:#fdeae5,stroke:#d8452f,stroke-width:1.5px,color:#5a1e14;
  classDef free fill:#e6f5ef,stroke:#0e8a6f,stroke-width:1.5px,color:#0c3b32;
  classDef info fill:#eef1f4,stroke:#8a94a3,color:#232a33;
  class cp,ng,n1 bill;
  class s3,ddb,ghoidc,bud,igw,sn1,sn2,oidc,access,addons free;
  class dev info;
```

## 리소스 구성 (생성 순서 = 의존 순서, destroy는 역순)

| # | 리소스 | 파일 | 하는 일 | 비용 |
|---|--------|------|---------|:----:|
| ① | IAM 클러스터 역할 + `AmazonEKSClusterPolicy` | `iam.tf` | 컨트롤플레인이 ENI 등 생성할 신원 (`eks.amazonaws.com`) | $0 |
| ② | `aws_eks_cluster` | `cluster.tf` | 컨트롤플레인 본체. public endpoint · `auth=API` | **$0.10/hr** |
| ③ | `aws_iam_openid_connect_provider` | `cluster.tf` | 클러스터 OIDC → IAM 등록 = **IRSA 토대** | $0 |
| ④ | IAM 노드 역할 + 정책 ×3 | `iam.tf` | 노드 조인·CNI·이미지풀 (`ec2.amazonaws.com`) | $0 |
| ⑤ | `aws_eks_node_group` | `nodes.tf` | 관리형 노드그룹 t4g.small Spot ×1 (min1/max2) | Spot ~$0.007/hr + EBS |
| ⑥ | `aws_eks_addon` ×3 | `addons.tf` | vpc-cni·kube-proxy·coredns — 없으면 노드 `Ready` 안 됨 | $0 |
| ⑦ | Access Entry + policy association | `access.tf` | bootstrap-admin → ClusterAdmin (신형 API) | $0 |

## 레이어별 상태

| 레이어 | 내용 | 상태 | 비용 |
|--------|------|------|:----:|
| `0-bootstrap` | S3 tfstate · DynamoDB 락 · GitHub OIDC · Budgets | 적용됨 (#283) | $0 |
| `1-network` | VPC 10.0.0.0/16 · IGW · 퍼블릭 서브넷 ×2 (NAT 회피) | 적용됨 (#285) | $0 |
| `2-cluster` | 컨트롤플레인 · 노드그룹 · OIDC · 애드온 · Access Entry | 코드 완성 · plan 통과 · **apply 대기** | ~$0.11/hr |

## 비용 수명주기

- **apply → 실습 → 자리 뜰 때 `tofu destroy`** = 다시 $0.
- 안 부수면 ~$0.11/hr(하루 ~$2.6) 계속 샌다 → 매 세션 destroy가 규율.
- `tofu plan` 기준 **14개 생성**(역할2 · 정책첨부4 · 클러스터1 · OIDC1 · 노드그룹1 · 애드온3 · 엔트리1 · 연결1).
