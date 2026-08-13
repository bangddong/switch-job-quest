# AWS EKS 실습 — 정답 경로

> **용도**: "AWS free tier $200 EKS 실습기" 튜토리얼 소스. 처음 하는 사람이 **이 문서만 보고
> 처음부터 따라 하면 되는 상태**를 항상 유지한다.
>
> **유지 규칙** (상세: 루트 `CLAUDE.md` "EKS 작업 일지 규칙"):
> - 실패 시도는 여기 없다 (그건 `eks-migration-log.md`) — **동작 확인된 명령어만** 순서대로
> - 각 명령어에 기대 출력/확인 방법, 단계별 사전 조건(도구 버전·AWS 권한·앞 단계 산출물) 명시
> - 💰 비용 발생 시작 지점 표시 + 각 단계 cleanup 명령어
> - 방식이 바뀌면 과거 단계도 최종 방식으로 소급 갱신
> - **스크린샷 없음** — 전 과정이 IaC+CLI라 모든 확인을 명령어와 기대 출력으로 한다 ("이 문서에 스크린샷이 없는 이유" 참조)
>
> **최종 검증**: 이관 완료 후 클러스터 destroy → 이 문서만으로 처음부터 재현 성공해야 완료.

### 재현 검증 이력 — 이 문서를 얼마나 믿어도 되나

| 날짜 | 범위 | 결과 |
|---|---|---|
| **2026-08-11** | **무과금 정적 대조** — 도구 버전 · 참조 파일 경로 14개 · `tofu plan` 실측 · 의존 그래프 · 마이그레이션 개수 · 시크릿 키 개수 | **결함 6건 발견·수정** (아래) |
| **2026-08-12** | **유료 부분 실행** (29분) — Stage 0→3b 경로를 실제로 apply→앱기동→destroy. 단 **목적이 L-14/L-15 검증**이라 문서를 그대로 따라간 게 아니고, Stage 4·ALB는 미실행 | **결함 2건 추가 발견·수정** + 08-11 수정분 4개 실물 확인 ✅ |
| (미실시) | **유료 완전 재현** — 이 문서**만** 보고 처음부터 끝까지 | ⏳ 다음 유료 세션 |

> 🔎 **08-12를 "재현 검증"으로 세지 않는 이유**: 나는 이 문서를 *참조*했지 *따라가지* 않았다.
> 막히면 코드를 직접 읽어 해결했는데, 처음 하는 사람에겐 그 선택지가 없다. 문서만으로 완주
> 가능한지는 **여전히 미검증**이다. (그래도 08-12에 확인된 것들은 실물 대조라 값이 있다 — 아래.)

**08-12 유료 세션에서 실물로 확인된 것** (08-11 정적 수정분이 맞았는지):

| 확인 항목 | 결과 |
|---|---|
| §2-4 `validated` (데이터 있는 볼륨 재배포 시) | ✅ `Successfully validated 13 migrations` |
| §3b-5 static PV 3종 apply 출력 5줄 | ✅ 문자 그대로 일치 |
| 볼륨 부착 (`aws ec2 describe-volumes`) | ✅ `in-use /dev/xvdaa` |
| ESO 시크릿 키 개수 (4 + 3 = 7) | ✅ 일치 |
| `Plan: N to add, 0 to change, 0 to destroy` 불변식 | ✅ apply 29 = destroy 29 |

**08-12에 새로 발견한 2건**: ①in-cluster용 ExternalSecret 적용 단계가 문서에 없었다(2-3)
②`ESO_ROLE` 빈 값 가드가 없어 **에러 메시지가 IRSA 주석에 들어갔다**(2-2). 둘 다 수정함.

2026-08-11에 고친 6건. **전부 "문서를 읽어서는 안 보이고, 실제로 대조해야 나오는"** 종류였다:

| # | 결함 | 어디 |
|:--:|---|---|
| ① | `db_mode` 변수가 **한 번도 등장하지 않았다** — Stage 2↔3a를 가르는 유일한 스위치인데 | 0-1 (신설) |
| ② | *"Stage 0을 밟으면 RDS까지 생긴다"* — 기본값이 `in-cluster`라 **안 생긴다** | 먼저 읽을 것 |
| ③ | `Plan: 26 to add` — 실측 **29**. 게다가 **개수는 `db_mode`를 구분 못 한다**(양쪽 다 29) | 0-1 |
| ④ | *"RDS는 EKS와 병렬로 안 만들어진다"* — `tofu graph`상 **의존이 없다**(SOP는 08-07에 고쳤는데 여기만 남아 있었다) | 0-2 |
| ⑤ | `Successfully validated 12 migrations`를 *"빈 RDS에 처음부터 실행"* 설명과 나란히 — **동시에 참일 수 없다** | 2-4 |
| ⑥ | 앱 기동 확인이 `/health`(상수)뿐 — `readiness`·`probe`·`actuator`가 **전 문서에 0건**이었다 | 3b-7 |

> 🔑 **①③⑥이 같은 병이다 — 검사가 주장보다 헐겁다.** 개수는 모드를 구분하지 못하고,
> 상수 응답은 앱이 DB에 붙었는지 구분하지 못한다. 기대 출력을 적어두는 건 좋지만,
> **무엇을 구분하지 *못하는지*** 를 같이 적지 않으면 통과가 곧 안심이 된다.

---

## ⚠️ 먼저 읽을 것 — 레이어와 스테이지는 1:1이 아니다

이 문서에서 가장 헷갈리기 쉬운 지점이라 앞에 둔다.

- **IaC 레이어**(`0-bootstrap` / `1-network` / `2-cluster`)는 **인프라를 나누는 단위**다.
- **Stage 0~5**는 **학습 마일스톤**이다. "무엇을 새로 이해했는가"로 나뉜다.

**둘은 겹치지 않는다.** `2-cluster` 레이어 하나가 EKS 클러스터 + (RDS) + Secrets Manager + IRSA +
EBS CSI를 **한 번의 `tofu apply`로 전부** 만든다. 그래서:

- Stage 0을 밟는 순간 **Stage 3용 EBS CSI 애드온·IRSA까지 같이 생긴다.** "Stage 3에 가서야 생긴다"가 **아니다.**
- 아래 Stage 1·2는 **인프라를 더 만드는 단계가 아니라, 이미 만들어진 것을 K8s 쪽에서 쓰는 단계**다.

> 🔴 **단, RDS는 예외다 — `db_mode` 변수가 켜고 끈다** (기본값 `in-cluster` = **RDS 안 만듦**).
> 이 문서는 오랫동안 *"Stage 0을 밟는 순간 RDS까지 같이 생긴다"* 고 적어뒀는데, 지금 기본값으로
> 따라 하면 **RDS가 한 개도 안 생긴다.** 2026-08-11 재현 검증에서 발견해 고쳤다.
> 스위치의 정확한 사용법은 [0-1의 `db_mode` 절](#-db_mode--이-문서에서-가장-중요한-스위치) 참조.

> **왜 이렇게 뒀나 (트레이드오프)**: RDS를 `3-data` 같은 별도 레이어로 빼는 게 구조적으로는 더 깔끔하다.
> 그런데 **자동 정리 장치(리퍼)가 하드코딩된 `2-cluster` 디렉토리에서만 `tofu destroy`를 돌린다.**
> RDS가 그 밖에 있으면, 사람이 destroy를 잊었을 때 클러스터만 지워지고 **RDS는 영구 과금**된다.
> → **"깔끔한 레이어 분리"보다 "안전장치 사정권"을 택했다.** 학습 계정에선 이 판단이 맞다.

---

## 이 문서에 스크린샷이 없는 이유

의도적으로 없다. 전 과정이 **IaC(OpenTofu) + CLI**로 돌아가기 때문이다.

- 콘솔 클릭 순서는 **개념이 아니라 UI 트리비아**다. 같은 내용을 `budget.tf` 한 파일이 더 정확하게 말한다 —
  `include_credit = false`가 왜 필요한지는 코드 한 줄과 주석으로 보이지만, 스크린샷으로는 안 보인다.
- **AWS 콘솔 UI는 자주 바뀐다.** 캡처는 코드보다 훨씬 빨리 썩고, 썩은 캡처는 없느니만 못하다.
- 리소스 상태는 `aws ... describe-*` 출력으로 **텍스트로 확인**할 수 있고, 그게 복사·검색·비교가 된다.

그래서 이 문서는 **모든 확인 절차를 명령어와 기대 출력으로** 제시한다. 화면을 보고 눈으로 맞추는
단계는 없다. 실제로 이 프로젝트도 초기엔 콘솔로 예산을 만들고 캡처를 남겼는데,
그 절차가 통째로 `budget.tf`로 대체되면서 캡처도 함께 폐기했다(B-3 참조).

---

## 사전 조건 (전체 공통)

**도구** (07-28 실측 조합, 이 버전들로 전 과정 검증됨):

| 도구 | 버전 | 설치 | 쓰이는 곳 |
|---|---|---|---|
| OpenTofu | v1.12.4 | `brew install opentofu` | 전 단계 |
| AWS CLI | 2.36.2 | `brew install awscli` | 전 단계 |
| kubectl | v1.36.3 | `brew install kubectl` | Stage 0~ |
| Helm | v4.2.3 | `brew install helm` | Stage 2 (ESO 설치) |

> **kubectl은 클러스터 K8s 버전과 맞추면 경고가 적다**(여기선 둘 다 1.36).
> 💡 **도구 공백은 실제 세션 직전이 아니라 준비 단계에서 드러난다** — 두 번 겪었다
> (Stage 0 직전 kubectl 미설치, Stage 2 직전 helm 미설치). 과금 시작 전에 위 4개를 한 번에 확인할 것:
> ```bash
> command -v tofu kubectl aws helm && aws sts get-caller-identity
> ```

**계정·크레딧**:
- AWS 계정: 신규 계정 (2026-07-16 생성)
- 크레딧 (07-16 콘솔 실측): **$200.00 = $100 (AWS Free Tier 기본) + $20×5 (Explore AWS 활동:
  EC2·Bedrock·Lambda·Budgets·RDS)**. 전 건 만료 **2027-01-15**. 개설 시점 사용 $0.00
- 🔴 **RDS 프리티어 없음** — 2025-07 이후 신규 계정은 프리티어가 아니라 크레딧 구조다.
  `aws freetier get-free-tier-usage`로 확인했고, RDS는 단가가 그대로 청구된다.
- AWS Budgets 알림 + Cost Anomaly Detection은 **코드로 생성된다** — 아래 "레이어 스캐폴딩" B-3 참조.
  Stage 0(첫 과금)보다 반드시 먼저 apply한다.

**크레딧 잔여를 정확히 보는 법** (⚠️ 함정 있음):

```bash
aws ce get-cost-and-usage --time-period Start=2026-07-01,End=2026-07-29 \
  --granularity MONTHLY --metrics UnblendedCost \
  --filter '{"Dimensions":{"Key":"RECORD_TYPE","Values":["Usage","Credit"]}}' \
  --group-by Type=DIMENSION,Key=RECORD_TYPE
```
```
Usage   0.3282654451
Credit -0.3282654047
```

> 🔴 **`RECORD_TYPE` 분리 없이 조회하면 크레딧이 상계돼 순액 `$0.00000004`만 나온다** —
> "아직 아무것도 안 썼네"로 오독하기 딱 좋다. 실사용량을 보려면 반드시 `Usage`로 필터할 것.

---

## 레이어 스캐폴딩 — `0-bootstrap` (💰 $0, 비용 가드레일이 여기서 생긴다)

Stage 0의 첫 과금 apply를 하기 **전에** 반드시 끝내야 하는 단계다. 이유는 두 가지:

1. **state 저장소가 있어야 한다** — 이후 모든 레이어가 여기 만든 S3 버킷에 state를 둔다.
2. **예산·이상탐지 가드레일이 여기서 생긴다** — 돈이 나가기 시작하는 건 Stage 0부터인데,
   감시 장치는 그 **전에** 켜져 있어야 의미가 있다.

> 💰 **이 레이어는 전부 $0이다.** S3에 든 tfstate는 수 KB, DynamoDB는 온디맨드라 유휴 시 $0,
> 예산·이상탐지는 무료, ECR은 이미지 없으면 $0, IAM은 항상 무료.
> **그래서 상시 켜 둔다** — Stage 0~5를 destroy해도 이 레이어는 남긴다.

### B-0. 무엇이 만들어지는가

| 파일 | 리소스 | 왜 필요한가 |
|---|---|---|
| `backend-state.tf` | S3 버킷 + DynamoDB 락 테이블 | 원격 state 저장소와 동시 실행 잠금 |
| `backend.tf` | `backend "s3"` 블록 | 이 레이어가 **자기 state를 자기가 만든 버킷에** 두게 함 |
| `budget.tf` | Budget + 알림 3단계 | 절대 금액 초과 감시 |
| `cost-anomaly.tf` | 이상탐지 모니터 + 구독 | 패턴 이탈 감시 (예산이 못 잡는 것) |
| `ecr.tf` | ECR 레포 + lifecycle | Stage 1의 이미지 보관소 |
| `iam-github-oidc.tf` | OIDC 공급자 + 역할 | CI가 장기 액세스키 없이 AWS를 만지게 함 |

### B-1. 닭-달걀 문제 — state 저장소를 state 없이 만든다

여기 이 레이어만의 함정이 있다. **원격 state를 쓰려면 S3 버킷이 있어야 하는데, 그 버킷을 만드는 게
바로 이 코드다.** 아직 없는 버킷을 backend로 지정하면 `tofu init`이 실패한다.

→ **2단계로 푼다. 먼저 로컬 state로 버킷을 만들고, 그 다음 state를 그 버킷으로 이사시킨다.**

```bash
cd infra/aws-eks/0-bootstrap

# ① backend 블록을 잠시 비활성화 — 아직 버킷이 없으므로
mv backend.tf backend.tf.disabled

# ② 값 채우기: 이메일은 커밋되지 않는 tfvars에 둔다
cp terraform.tfvars.example terraform.tfvars
#    → terraform.tfvars 를 열어 budget_notification_email 을 본인 주소로

# ③ 버킷 이름은 S3 전역에서 유일해야 한다 — 그대로 쓰면 충돌한다
#    variables.tf 의 state_bucket_name 기본값을 본인 것으로 변경
#    (backend.tf.disabled 안의 리터럴 버킷명도 같이 바꿔야 한다 — 아래 ⚠️ 참조)

# ④ 로컬 state로 apply
tofu init
tofu apply
```

기대 출력: `Apply complete! Resources: N added` — 그리고 **로컬에 `terraform.tfstate` 파일이 생긴다**
(실측 4950B). 이 파일이 지금 이 레이어의 유일한 state다.

검증:
```bash
aws s3 ls | grep tfstate            # 버킷 생성 확인
aws dynamodb list-tables --query 'TableNames' --output text | tr '\t' '\n' | grep tflock
```

> ⚠️ **`backend` 블록에는 변수를 못 쓴다.** OpenTofu가 backend를 읽는 시점은 변수 평가 **전**이라
> `bucket = var.state_bucket_name` 같은 참조가 불가능하다. 버킷·테이블 이름은 리터럴이어야 하고,
> 그래서 이름을 바꿀 때 `variables.tf`와 `backend.tf` **두 곳을 손으로 맞춰야** 한다.
> 처음 하는 사람이 제일 자주 밟는 지점이다.

### B-2. state를 S3로 이관

```bash
mv backend.tf.disabled backend.tf
tofu init -migrate-state -force-copy
```

기대 출력: `Successfully configured the backend "s3"!`

검증 — **세 가지를 다 봐야 이관이 진짜 끝난 것이다**:
```bash
aws s3 ls s3://<본인-버킷>/0-bootstrap/   # terraform.tfstate 가 있어야 함 (실측 4950B)
ls -l terraform.tfstate                    # 0바이트로 비어야 함 (.backup 은 남는다 — 정상)
tofu plan                                  # No changes. 여야 함 (드리프트 0)
```

> `-force-copy`는 "state를 복사할까요?"라는 대화형 확인을 자동 통과시킨다. 로컬 `.backup`이 남으므로
> 되돌릴 수 있어 안전하다. 이 플래그가 없으면 비대화형 셸에서 `EOF` 에러로 멈춘다.
>
> **이제 이 스택은 자기 state를 자기가 만든 버킷에 둔다**(자기참조 backend). state 키에 레이어명을
> prefix로 주기 때문에(`0-bootstrap/terraform.tfstate`) 1-network·2-cluster와 **버킷 하나를 공유하되
> 충돌하지 않는다.**

### B-3. 비용 가드레일이 왜 코드인가 (콘솔로 하지 않는 이유)

예산은 콘솔에서 클릭으로도 만들 수 있다. 실제로 이 프로젝트도 처음엔 그렇게 했다.
**그런데 콘솔 절차에는 사람이 반드시 밟는 함정이 두 개 있었다:**

| 함정 | 콘솔에서 벌어지는 일 | 코드에서는 |
|---|---|---|
| **크레딧 상계** | 크레딧이 청구액을 $0으로 가려 **알림이 영영 안 울린다** | `cost_types { include_credit = false }` |
| **임계값 단위** | 기본값이 `% of budgeted amount` → 10/50/150이 **$20/$100/$300**이 된다 ($200 예산 기준 150%는 크레딧 초과라 무의미) | `threshold_type = "ABSOLUTE_VALUE"` |

즉 **코드로 옮기니 함정이 사라진 게 아니라, 함정을 밟을 기회 자체가 없어졌다.**
"조심하세요"라고 문서에 쓰는 것과, 틀린 값을 애초에 표현할 수 없게 만드는 것의 차이다.
`budget.tf`·`cost-anomaly.tf`의 주석이 각 설정이 왜 그 값인지 설명하니 **코드를 읽는 게 곧 학습**이다.

**두 장치의 역할이 다르다**:
- **예산** = 절대 금액 감시. `$10 / $50 / $150` 초과 시 이메일.
- **이상탐지** = 패턴 감시. 금액이 작아도 "안 쓰던 서비스가 갑자기 켜졌다"를 잡는다. 즉 **끄는 걸 잊은 리소스** 탐지용.

> 🔴 **둘 다 실시간이 아니다 — 기대치를 정확히 해 둘 것.** 예산은 `ACTUAL` 비용 기준이라 AWS 청구
> 반영(~24h)을 기다리고, 이상탐지는 이메일 구독자에게 **`DAILY` 요약이 최선**이다
> (`IMMEDIATE` 즉시 알림은 SNS 구독자에게만 허용된다 — EMAIL로 지정하면 apply가 거부한다).
> **30분짜리 세션을 지키는 실시간 장치가 아니다.** 그 역할은 리퍼(dead man's switch)가 하고,
> 이 둘은 **리퍼까지 실패했을 때 걸리는 마지막 그물**이다.

#### 🔴 apply 전에 반드시 — 이상탐지는 **만드는 게 아니라 인수한다**

`cost-anomaly.tf`를 그대로 apply하면 **실패한다**. 실제로 밟은 에러:

```
Error: creating Cost Explorer Anomaly Monitor (devquest-eks-service-monitor):
api error ValidationException: Limit exceeded on dimensional spend monitor creation
```

**원인**: AWS가 신규 계정에 `Default-Services-Monitor`를 **미리 만들어 둔다.** 그리고
DIMENSIONAL(SERVICE) 모니터는 **계정당 1개**만 허용된다. 없는 걸 만드는 게 아니라 **이미 있는 걸
중복 생성**하려 한 것이다.

→ **먼저 state로 인수(import)한 뒤 apply한다:**

```bash
cd infra/aws-eks/0-bootstrap

# ① 먼저 "무엇이 있는지" 눈으로 본다 — 인수는 되돌리기 번거로우니 대상을 확인하고 시작한다
aws ce get-anomaly-monitors      --query 'AnomalyMonitors[].[MonitorName,MonitorType,MonitorDimension]' --output table
aws ce get-anomaly-subscriptions --query 'AnomalySubscriptions[].[SubscriptionName,Frequency]'          --output table

# ② 이름으로 특정해서 ARN을 뽑는다 (인덱스 [0]을 쓰지 않는 이유는 아래 ⚠️)
MON=$(aws ce get-anomaly-monitors \
  --query "AnomalyMonitors[?MonitorName=='Default-Services-Monitor'].MonitorArn | [0]" --output text)
SUB=$(aws ce get-anomaly-subscriptions \
  --query "AnomalySubscriptions[?SubscriptionName=='Default-Services-Subscription'].SubscriptionArn | [0]" --output text)

# ③ 제대로 잡혔는지 확인 — None 이면 이름이 다르다는 뜻이니 ①의 출력을 보고 이름을 맞춘다
[ "$MON" != "None" ] && [ "$SUB" != "None" ] && echo "OK" || echo "이름 불일치 — ①의 목록에서 확인할 것"

tofu import aws_ce_anomaly_monitor.services    "$MON"
tofu import aws_ce_anomaly_subscription.alerts "$SUB"
```

> ⚠️ **`AnomalyMonitors[0]` 같은 인덱스 접근을 쓰지 마라.** 신규 계정은 모니터가 1개뿐이라 우연히
> 맞지만, **커스텀 모니터가 이미 있는 계정**(회사 계정, 또는 이 튜토리얼을 한 번 돌린 계정)에서는
> **엉뚱한 리소스를 인수**하게 된다. 그러면 이후 apply가 그 리소스의 이름을 바꾸거나 재생성해
> 남의 설정을 망가뜨린다. 이름으로 특정하고, ①에서 눈으로 확인하고 시작할 것.

인수 후 `tofu plan`이 무엇을 하려는지 **반드시 확인한다** — 여기서 갈린다:

| 리소스 | 계획 | 왜 |
|---|---|---|
| 모니터 | `updated in-place` | 이름 변경은 in-place ✅ |
| 구독 | `must be replaced` | `name`이 `forces replacement` — 무료·재생성 가능이라 안전 |

> ⚠️ **모니터가 `replaced`로 나오면 멈춰라.** 재생성은 삭제 후 생성이라 **위 한도 에러를 다시 밟는다.**
>
> 💡 **왜 `import` 블록으로 코드화하지 않았나**: 리소스 UUID가 계정마다 달라 코드에 박으면 이식되지
> 않는다(게다가 ARN에 계정 ID가 들어가 공개 레포에 못 쓴다). 이 단계는 계정당 한 번, 사람이 하는 게 맞다.

#### 🔴 AWS 기본값을 그대로 두면 안 된다

인수해 온 **기본 구독의 임계값은 `$100 이상 AND 40% 이상`**이었다.
크레딧 총액이 $200인 학습 계정에서 **절반이 날아간 뒤에야 울리는** 값이다.

**"기본값이 있으니 됐다"가 가장 위험한 상태다** — 켜져 있는데 안 울린다.
그래서 `$5`(예산 1단계 $10보다 낮게)로 낮춘다.

검증 (apply 후):
```bash
aws budgets describe-notifications-for-budget \
  --account-id "$(aws sts get-caller-identity --query Account --output text)" \
  --budget-name devquest-eks-monthly \
  --query 'Notifications[].[ComparisonOperator,Threshold,ThresholdType]' --output table
aws ce get-anomaly-monitors --query 'AnomalyMonitors[].[MonitorName,MonitorDimension]' --output table
aws ce get-anomaly-subscriptions \
  --query 'AnomalySubscriptions[].[SubscriptionName,Frequency,ThresholdExpression.Dimensions.Values[0]]' \
  --output table
```
기대: 알림 3건이 전부 `ABSOLUTE_VALUE` `10 / 50 / 150`, 모니터 1건이 `SERVICE`,
구독 1건이 `DAILY` + 임계값 `5`(기본 `100.0`이 아니라).

### B-4. 이후는 CI가 apply한다

여기까지가 사람이 손으로 하는 유일한 부트스트랩이다. 그 다음부터 `0-bootstrap`·`1-network`는
**PR을 열면 `tofu plan`, main에 머지되면 `tofu apply`**가 자동으로 돈다
(`.github/workflows/infra-deploy.yml`, GitHub OIDC로 역할 assume — 장기 액세스키 없음).

> 🔴 **`2-cluster`는 이 자동 목록에 절대 넣지 않는다.** 머지 순간 CI가 EKS 컨트롤플레인($0.10/hr)을
> 띄우고 아무도 destroy하지 않으면 상시 과금이 샌다. Infra CI의 `guard-local-layers` 잡이 이 실수를
> **기계적으로 차단**한다 — 규율을 사람 기억에 맡기지 않는다.

### B-5. `1-network` · `2-cluster`

이 둘은 닭-달걀이 없어 평범하다 — backend가 이미 있으므로 `init` → `plan` → `apply`가 전부다.

```bash
tofu -chdir=infra/aws-eks/1-network init && tofu -chdir=infra/aws-eks/1-network apply
```

`1-network`(VPC·서브넷·IGW)도 **$0**이다 — NAT 게이트웨이를 쓰지 않기 때문이다(퍼블릭 서브넷 + 노드에
퍼블릭 IP 부여로 대체). NAT는 시간당 $0.045 + 데이터 처리 요금이라 켜 두면 학습 계정에서 가장 조용히
크레딧을 갉아먹는 리소스다.

`2-cluster`는 **과금 대상**이므로 Stage 0에서 다룬다.

> 🟡 **재현 검증 상태**: 위 명령들은 전부 실제로 실행해 출력을 확인한 것이다. 다만 우리는 코드를
> **점진적으로 작성하며 여러 번 나눠 apply**했고, 위 B-1은 "코드가 이미 다 있는 상태에서 한 번에"로
> 정리한 순서다. 이 통합 순서 그대로의 처음부터 재현은 아직 해 보지 않았다
> (`docs/eks-migration-log.md`의 07-16~07-18 항목이 원본 기록).

---

## Stage 0 — 클러스터 왕복 (★ 첫 과금)

**이 단계가 하는 일**: EKS 컨트롤플레인 + 워커 노드 1대 + 애드온 + (RDS·시크릿·IRSA)를 띄우고,
kubectl로 노드가 뜬 걸 확인한 뒤 **곧바로 부순다.**

"떴다 부순다"를 한 세션에 왕복하는 것이 destroy-after-use 규율의 핵심이다 — 클러스터를 켜둔 채
방치하는 시간이 곧 비용이기 때문이다(컨트롤플레인은 워크로드가 0이어도 **$0.10/hr 고정**).

> 💡 **처음이라면 Stage 0만 왕복해보고 끝내는 것을 권한다.** apply→검증→destroy를 손에 익힌 뒤
> Stage 1·2로 가는 편이, 과금 중에 처음 보는 명령을 더듬는 것보다 훨씬 싸다.

### 0-0. 사전 조건
- 위 "사전 조건"의 도구 4종 + 자격증명
- 선행 레이어(`0-bootstrap`·`1-network`)가 이미 apply되어 S3 backend·VPC가 존재해야 함
- **K8s 버전 재확인**(비용 $0): 핀한 버전이 아직 표준지원인지 apply 직전에 본다.
  ```bash
  aws eks describe-cluster-versions --region ap-northeast-2 \
    --query 'clusterVersions[?status==`STANDARD_SUPPORT`].[clusterVersion,endOfStandardSupportDate]' \
    --output table
  ```
  확인: 핀한 버전(예: 1.36)이 목록에 있고 종료일이 넉넉해야 함. **필터 필드는 `status`**(오타 시 빈 출력).
  - 실측(07-28): 1.36은 2027-08-02까지. **1.33은 2026-07-29 종료** — 낮은 버전에 핀해뒀다면 여기서 걸린다.

- **RDS 엔진 버전·인스턴스 클래스 실존 확인** (apply 최다 실패 지점, 비용 $0):
  ```bash
  aws rds describe-db-engine-versions --engine postgres --engine-version 17.10 \
    --region ap-northeast-2 --query 'DBEngineVersions[].EngineVersion' --output text
  aws rds describe-orderable-db-instance-options --engine postgres --engine-version 17.10 \
    --db-instance-class db.t4g.micro --region ap-northeast-2 \
    --query 'OrderableDBInstanceOptions[].DBInstanceClass' --output text
  ```
  > **왜 미리 보나**: 코드에만 있고 AWS엔 없는 버전이면 apply가 **RDS 생성 단계에서** 죽는다.
  > 그땐 이미 EKS가 만들어져 **과금 중**이라 제일 비싼 실패다. ARM 인스턴스는 리전·버전별로
  > 주문 불가한 조합이 있으니 클래스도 함께 본다.

### 0-1. init + plan (비용 $0)
```bash
tofu -chdir=infra/aws-eks/2-cluster init
tofu -chdir=infra/aws-eks/2-cluster plan -out=/tmp/stage.tfplan
```
🔴 **확인 기준은 개수가 아니라 뒤의 두 값이다:**

```
Plan: <N> to add, 0 to change, 0 to destroy.
                  ^^^^^^^^^^^^^^^^^^^^^^^^^  ← 여기가 불변식
```

`0 to change, 0 to destroy`는 **스테이지·버전이 올라가도 변하지 않는다.** 아무것도 없는 상태에서
새로 짓는 것이므로 고치거나 부술 대상이 있으면 안 된다. 하나라도 0이 아니면 **state가 실제 AWS와
어긋나 있다**는 뜻이니 apply하지 말고 원인을 찾는다.

> ⚠️ **`N`(추가 개수)을 합격 기준으로 삼지 마라.** 두 가지 이유로 못 쓴다.
>
> **① 스테이지가 올라가면 늘어난다.** `14`(07-24) → `26`(07-28) → **`29`(2026-08-11 실측)**.
> Stage 3a에서 EBS CSI 애드온+IRSA가, 3a-5에서 postgres TLS 리소스가 같은 레이어에 붙었다.
> 이 문서가 오래 `26`을 확인 기준으로 적어둔 탓에, 그대로 따라 하면 **맞게 했는데도 틀린 것처럼
> 보였다**(2026-08-11 재현 검증에서 발견). 기대 출력을 적는 건 좋지만 **갱신 안 된 기대 출력은
> 사람을 막는다.**
>
> **② 🔴 개수는 `db_mode`를 구분하지 못한다.** 실측:
> ```
> tofu plan                      → Plan: 29 to add    (in-cluster, 기본값)
> tofu plan -var db_mode=rds     → Plan: 29 to add    (RDS)
> ```
> RDS 4개가 빠진 자리에 TLS 2 + 시크릿 2가 들어와 **우연히 같아진다.**
> *"29가 나왔으니 제대로 설정됐다"* 가 성립하지 않는다 — **검사가 주장보다 헐거운** 전형이다.
> 모드를 확인하려면 개수가 아니라 **RDS 리소스가 계획에 있는지**를 본다:
> ```bash
> tofu -chdir=infra/aws-eks/2-cluster show -json /tmp/stage.tfplan \
>   | grep -c '"aws_db_instance"'      # rds 모드면 ≥1, in-cluster면 0
> ```

#### 🔑 `db_mode` — 이 문서에서 가장 중요한 스위치

```hcl
# infra/aws-eks/2-cluster/variables.tf
variable "db_mode" {
  default = "in-cluster"   # rds = 관리형(Stage 2) / in-cluster = Postgres StatefulSet(Stage 3a~)
}
```

<!-- verify: infra/aws-eks/2-cluster/variables.tf ~ variable "db_mode" -->
<!-- verify: infra/aws-eks/2-cluster/variables.tf ~ default[[:space:]]*=[[:space:]]*"in-cluster" -->

| 하려는 것 | 명령 | 왜 |
|---|---|---|
| **Stage 0~1** (클러스터 왕복 · 이미지 배포) | 아무것도 주지 않는다 — **기본값** | DB가 필요 없는 단계다. 기본값이면 RDS가 안 생겨 **$0.025/hr을 아낀다** |
| **Stage 2** (RDS 연결 실습) | `... plan -var db_mode=rds -out=/tmp/stage.tfplan` | 🔴 **이걸 빼면 Stage 2가 성립하지 않는다** |
| **Stage 3a·3b** (in-cluster Postgres) | 아무것도 주지 않는다 — **기본값** | RDS를 in-cluster로 갈아끼우는 게 목적 |

🔴 **기본값이 `in-cluster`인 건 의도다.** 플래그를 잊었을 때 **과금 리소스(RDS)가 생기지 않는 쪽**이
기본이어야 한다. 대신 대가가 있다: **Stage 2를 `-var db_mode=rds` 없이 따라 하면 RDS가 한 개도
생기지 않고**, 2-4에서 기다리는 `Database: jdbc:postgresql://...rds.amazonaws.com` 이 영영 안 나온다.
그리고 위 ②대로 **개수는 그 실수를 잡아주지 못한다.**

> 📌 이 변수는 2026-08-11까지 이 문서에 **한 번도 등장하지 않았다**(grep 0건). Stage 2와 Stage 3a를
> 가르는 유일한 스위치인데도 그랬다 — 재현 검증에서 나온 결함 중 가장 컸다.

구성 (2026-08-11 실측, 두 모드 모두 **29**):

| 분류 | in-cluster | rds | 과금 |
|---|:--:|:--:|---|
| EKS 컨트롤플레인 | 1 | 1 | **$0.10/hr** |
| 관리형 노드그룹 (t4g.small ×1) | 1 | 1 | **$0.0208/hr** + EBS 20GB |
| 애드온 (vpc-cni·kube-proxy·coredns·**aws-ebs-csi-driver**) | 4 | 4 | $0 |
| IAM (OIDC 1 · 역할 4 · 정책 1 · 연결 6 · access entry·assoc 2) | 14 | 14 | $0 |
| Secrets Manager (시크릿 + 버전 쌍) | 6 | 4 | $0.40/개/월 |
| `random_password` (JWT 키) | 1 | 1 | $0 |
| **postgres TLS** (`tls_private_key` · `tls_self_signed_cert`) | 2 | 0 | $0 |
| **RDS** (인스턴스·서브넷그룹·보안그룹·인그레스) | 0 | 4 | **$0.025/hr** + gp3 20GB |
| **합계** | **29** | **29** | |

- plan에서 **`capacity_type = "ON_DEMAND"`** 확인 — 신규 계정은 Spot vCPU 쿼터가 0이라 SPOT이면 apply가 실패한다.
- 📌 **레이어와 스테이지는 1:1이 아니다** — `2-cluster` 한 레이어가 Stage 0~3b의 리소스를 전부 담는다.
  그래서 "Stage 0을 하는데 EBS CSI 애드온이 왜 생기지?"가 정상이다. 문서 상단 동명의 절 참조.

### 0-2. apply (★ 과금 시작)
```bash
tofu -chdir=infra/aws-eks/2-cluster apply -auto-approve /tmp/stage.tfplan
```
확인: `Apply complete! Resources: <N> added.` — plan에서 본 `N`과 같아야 한다. **약 9~15분.**

단계별 실측 소요:

| 리소스 | 소요 |
|---|---|
| EKS 컨트롤플레인 | ~6분 (07-28) · 6분 1초 (08-07) |
| 노드그룹 | 2분 48초 |
| 애드온 vpc-cni·kube-proxy | 55초 / coredns 24초 |
| **RDS db.t4g.micro** | **4분 50초** |
| 전체 왕복 (in-cluster) | apply 8분 27초 · destroy 6분 21초 (08-07 실측) |

> ✅ **RDS는 EKS와 *병렬로* 만들어진다** (2026-08-07 실측 + 의존 그래프 확인).
> ```
> aws_db_instance.main[0]: Creating...      aws_eks_cluster.main: Creating...   ← 동시 시작
> aws_db_instance.main[0]: Creation complete after 4m56s
> aws_eks_cluster.main:    Creation complete after 6m1s
> ```
> **RDS 4분 56초가 EKS 6분 1초 안에 통째로 들어갔다.** `tofu graph`로도 확인된다 —
> `aws_db_instance.main`의 선행 노드는 `aws_db_subnet_group` · `aws_security_group.rds`뿐이고
> **`aws_eks_cluster`는 들어있지 않다.** 클러스터 보안그룹을 참조하는 건
> `aws_vpc_security_group_ingress_rule`(별도 리소스)이라 사슬이 DB까지 이어지지 않는다.
>
> ```bash
> tofu -chdir=infra/aws-eks/2-cluster graph -type=plan | grep '"\[root\] aws_db_instance'
> ```
>
> ⚠️ **이 자리에는 원래 정반대 서술이 있었다** — *"의존 사슬이 생겨 직렬화되므로 RDS 시간이
> EKS 시간에 더해진다"*. 벽시계가 길어진 걸 보고 **원인을 추론해 문서를 고친 것**이었고,
> 의존 그래프를 열어보지 않았다. **근거를 확인하지 않은 정정은 원래 서술보다 나쁘다** —
> 틀린 데다 "검증됨" 딱지가 붙는다. (SOP는 08-07에 고쳤는데 이 문서는 2026-08-11
> 재현 검증까지 옛 서술을 들고 있었다 — **같은 사실이 두 문서에 있으면 갈라진다.**)

**이 순간부터 과금. 자리를 뜨지 말 것.**

#### 함정 — apply가 "멈춘 것처럼" 보일 때

OpenTofu는 **리소스 에러를 즉시 출력하지 않고 apply 종료 시점에 모아서 낸다.** 그래서 실패한
리소스가 `Creating...`에 머물러 있는 것처럼 보인다. 게다가 `tofu state list`도 apply 중엔
pre-apply 상태(0건)라 진단에 쓸 수 없다.

→ **CloudTrail 이벤트 원문**으로 확인한다:
```bash
aws cloudtrail lookup-events --region ap-northeast-2 \
  --lookup-attributes AttributeKey=EventName,AttributeValue=CreateSecurityGroup \
  --max-results 5 --output json \
  | ruby -rjson -e 'JSON.parse(STDIN.read)["Events"].each{|ev| e=JSON.parse(ev["CloudTrailEvent"]); puts "#{e["eventTime"]} err=#{e["errorCode"].inspect} #{e["errorMessage"].to_s[0,200]}"}'
```
> 🔴 **`lookup-events`의 요약 필드 `ErrorCode`는 `None`으로 나온다** — 요약만 보면 "정상 호출"로
> 오독한다. 반드시 `CloudTrailEvent` **원문 JSON**을 파싱해야 `errorCode`가 보인다.
>
> 참고로 IAM은 글로벌 서비스라 **이벤트가 `us-east-1`에 기록**된다. IAM 관련 실패를 볼 땐 리전을 바꿀 것.

#### 함정 — `description`에 한글을 쓰면 apply가 깨진다 (도구가 못 잡는다)

`tofu validate`·`tofu plan`·`tfsec`이 **셋 다 통과시킨다.** AWS API를 호출하지 않기 때문이다.

```
InvalidParameterValue: Value (...) for parameter GroupDescription is invalid.
  Character sets beyond ASCII are not supported.

ValidationError: Value at 'description' failed to satisfy constraint:
  Member must satisfy regular expression pattern: [	

 -~¡-ÿ]*
```

제약은 **서비스마다 다르다** (같은 apply 안에서 대조 확인):

| 서비스 | 한글 description |
|---|:--:|
| EC2 보안그룹 / 인그레스 규칙 | ❌ |
| IAM 역할 / 정책 | ❌ |
| Secrets Manager | ✅ |
| ECR lifecycle policy | ✅ |

- `resource` 블록의 `description`만 위험하다. `variable`/`output`의 `description`은
  **로컬 메타데이터**라 AWS로 안 가므로 한글 무방.
- 검사:
  ```bash
  grep -rnP 'description\s*=.*[^\x00-\x7F]' --include='*.tf' infra/
  ```

### 0-3. 검증 — 노드 Ready
```bash
aws eks update-kubeconfig --name devquest-eks --region ap-northeast-2
kubectl get nodes -o wide
kubectl get pods -n kube-system
```
확인:
- 노드 1개 `STATUS=Ready`, `VERSION=v1.36.x`, 아키텍처 arm64(Graviton), `EXTERNAL-IP`에 공인 IP가 붙음
  (퍼블릭 서브넷 + 공인 IP = NAT Gateway를 피한 설계. NAT는 월 $32라 학습 클러스터에선 회피).
- `kube-system`에 `aws-node`(vpc-cni)·`coredns` ×2·`kube-proxy`가 전부 `Running`.

**노드에 파드가 몇 개나 들어가나** (Stage 1·2에서 중요해진다):
```bash
kubectl get node -o jsonpath='{.items[0].status.allocatable.pods}'
```
확인: **`11`**

> **왜 11인가**: EKS VPC CNI는 파드에 **VPC의 실제 IP**를 준다. 인스턴스 타입마다 붙일 수 있는
> ENI 수와 ENI당 IPv4 수가 정해져 있고, `t4g.small`은 **ENI 3개 × IPv4 4개**다.
> 각 ENI의 첫 IP는 ENI 자체가 쓰므로 `3 × (4-1) + 2 = 11`.
> 이 계산이 실측과 정확히 일치한다. **시스템 파드 4개(aws-node·kube-proxy·coredns×2)를 빼면 여유는 7개.**

### 0-4. destroy (★ 과금 종료 — 검증 직후 즉시)

Stage 1·2로 이어갈 게 아니면 **바로 부순다.**

```bash
tofu -chdir=infra/aws-eks/2-cluster destroy -auto-approve
```
확인: `Destroy complete! Resources: <N> destroyed.` — **apply한 개수와 같아야 한다.**
소요는 **약 4분 30초**(07-28, rds) ~ **6분 21초**(08-07, in-cluster).
(노드그룹 2분 16초 · RDS 3분 53초 · 컨트롤플레인 2분 9초 — 병렬로 진행)

> 🔴 **여기서 봐야 할 건 `<N>`이 apply와 *같다*는 것이지 특정 숫자가 아니다.** 개수가 모자라면
> destroy가 일부 실패했다는 뜻이고, 실패한 채 자리를 뜨면 그게 그대로 과금이다. 0-5로 이어간다.

### 0-5. teardown 전수 검증 (고아 리소스 = 계속 새는 비용)

```bash
R=ap-northeast-2
tofu -chdir=infra/aws-eks/2-cluster state list      # 비어 있어야 함
aws eks list-clusters --region $R
aws elbv2 describe-load-balancers --region $R --query 'LoadBalancers[].LoadBalancerName'
aws ec2 describe-volumes --region $R --filters Name=status,Values=available --query 'Volumes[].VolumeId'
aws ec2 describe-nat-gateways --region $R --filter Name=state,Values=available
aws ec2 describe-instances --region $R --filters Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].InstanceId'

# ── RDS 계열 (인스턴스가 사라져도 스냅샷은 남아 과금된다) ──
aws rds describe-db-instances --region $R --query 'DBInstances[].DBInstanceIdentifier'
aws rds describe-db-snapshots --region $R --snapshot-type manual    --query 'DBSnapshots[].DBSnapshotIdentifier'
aws rds describe-db-snapshots --region $R --snapshot-type automated --query 'DBSnapshots[].DBSnapshotIdentifier'
aws rds describe-db-subnet-groups --region $R --query 'DBSubnetGroups[].DBSubnetGroupName'

# ── Secrets Manager (--include-planned-deletion 없으면 "삭제 대기"가 안 보인다) ──
aws secretsmanager list-secrets --region $R --include-planned-deletion \
  --query 'SecretList[].{Name:Name,Deleted:DeletedDate}'
```

**전부 비어 있어야 한다.** 실측(07-28) 결과 전 항목 0건.

- ⚠️ **왜 육안 확인이 필요한가**: `tofu destroy`는 **state에 있는 것만** 지운다.
  Stage 3부터 생기는 **ALB Ingress·EBS PVC는 K8s가 만든 것이라 state 밖**이며, destroy 후에도
  남아 계속 과금된다. 그때는 destroy 전에 `kubectl delete ingress,pvc --all -A`를 먼저 해야 한다.
- ✅ **RDS 마스터 시크릿은 자동 정리된다** (실측 확인): `manage_master_user_password = true`로
  AWS가 만든 `rds!db-<uuid>` 시크릿은 인스턴스 삭제와 함께 **완전히 사라진다.**
  복구창 좀비도, 이름 점유도 남지 않는다.
- 우리가 만든 시크릿 2개는 코드에서 **`recovery_window_in_days = 0`** 으로 둬서 즉시 소멸한다.
  기본값(30일)이면 시크릿당 **$0.40/월**이 계속 나가고, **이름이 점유돼 다음 apply가
  `InvalidRequestException`으로 실패**한다.
- EC2가 `terminated`로 잠시 보이는 건 정상(종료 인스턴스는 ~1시간 잔상만, 과금 없음).

### Stage 0 비용 결산 (실측)

| 세션 | 벽시계 | 비용 |
|---|---|---|
| 2026-07-24 (클러스터만, 14 리소스) | ~50분 | ~$0.10 |
| 2026-07-28 (Stage 2 포함, 26 리소스) | **26분 35초** | **≈$0.06** |

> **아낄 것은 크레딧이 아니라 "켜놓고 딴짓하는 시간"이다.** 리소스를 늘렸는데도 비용이 줄었다 —
> destroy를 미루지 않았고, 대기 시간에 다른 작업(문서·ESO 설치)을 병렬로 처리해 벽시계가 짧아졌기 때문.

---

## Stage 1 — ECR 이미지 → 노드로 pull → 앱 배포

**이 단계의 목표**: "ECR에 올린 이미지가 **노드로 실제로 내려와** 파드가 뜨는가."
DB 연결은 아직 안 한다. **앱이 CrashLoop에 빠지는 것이 정상이며, 그 진단 자체가 이 단계의 교보재다.**

### 1-1. 이미지 빌드·푸시 (클러스터 없어도 가능, 💰 $0)

GitHub Actions의 **ECR Push** 워크플로로 빌드한다.

> **왜 로컬에서 안 만드나**: 로컬 빌드는 "그 사람 노트북 상태"가 이미지에 섞인다.
> CI는 매번 동일한 깨끗한 환경 + 감사 로그(누가/언제/어떤 커밋)가 남는다.

> 🔴 **함정 — 임의 브랜치 push로는 안 된다.**
> ```
> Could not assume role with OIDC: Not authorized to perform sts:AssumeRoleWithWebIdentity
> ```
> OIDC 역할의 신뢰정책이 `refs/heads/main`과 `pull_request`만 허용하기 때문이다.
> 브랜치 push의 `sub`은 `repo:<owner>/<repo>:ref:refs/heads/<branch>`라 매칭에 실패한다.
> **보안이 의도대로 작동한 것**이므로 신뢰정책을 푸는 대신(그 역할은 AdministratorAccess를 갖는다)
> **PR을 열어 `pull_request` 컨텍스트에서 빌드한다.**
> (더 정석: ECR 전용 최소권한 역할을 따로 만들어 브랜치를 넓히기)
>
> `workflow_dispatch`는 **기본 브랜치(main)에 워크플로 파일이 있을 때만** 실행 가능하다
> (`--ref`로 브랜치를 줘도 정의는 main에서 찾는다 → 없으면 HTTP 404).

빌드가 끝나면 이미지 주소가 Step Summary에 찍힌다:
```
<account>.dkr.ecr.ap-northeast-2.amazonaws.com/devquest/core-api:<git-sha>
```


> 🔴 **CLI로 태그를 뽑을 땐 `imageTags[0]`을 쓰지 마라.** 한 이미지는 태그를 여러 개 갖고
> (`<sha>` + `latest`) **배열 순서는 보장되지 않는다** — 그대로 쓰면 금지된 `latest`로 배포된다.
> 40자리 hex만 골라야 한다:
> ```bash
> SHA=$(aws ecr describe-images --repository-name devquest/core-api --region ap-northeast-2 \
>   --query 'sort_by(imageDetails,&imagePushedAt)[-1].imageTags' --output json \
>   | ruby -rjson -e 'puts JSON.parse(STDIN.read).find{|t| t =~ /\A[0-9a-f]{40}\z/}')
> ```

> ⚠️ **아키텍처가 맞아야 한다.** 노드가 arm64(Graviton)이므로 이미지도 **arm64**여야 한다.
> x86 이미지를 올리면 파드가 `exec format error`로 CrashLoop에 빠진다.
> 그래서 워크플로가 **arm64 러너에서 네이티브 빌드**한다(에뮬레이션 크로스빌드보다 빠르고 확실).

> 💡 **Dockerfile jar 글롭 함정**: `cp build/libs/*.jar`는 모듈에 따라 깨진다.
> plain jar가 켜진 모듈은 산출물이 2개(`x.jar` + `x-plain.jar`)다.
> `-plain` 제외 + 개수 검증 후 복사할 것.

### 1-2. 배포

```bash
REPO=$(aws ecr describe-repositories --repository-names devquest/core-api \
  --region ap-northeast-2 --query 'repositories[0].repositoryUri' --output text)
IMAGE="$REPO:$SHA"        # 위에서 뽑은 40자리 hex

sed "s|IMAGE_PLACEHOLDER|$IMAGE|" k8s/base/core-api.yaml | kubectl apply -f -
```

> **Service를 `LoadBalancer`가 아니라 `ClusterIP`로 두는 이유 = 비용.**
> `LoadBalancer`로 만들면 K8s가 AWS에 NLB를 자동 생성하는데, 이건 **tofu state 밖**이라
> destroy 때 고아로 남아 계속 과금된다. 외부 접근이 필요하면 `kubectl port-forward`로 충분하다(무료).

### 1-3. 검증 — ★ 이 단계의 목표

```bash
kubectl describe pod -l app=core-api | grep -A3 "Pulled\|Pulling"
```
확인:
```
Normal  Pulled  kubelet  Successfully pulled image ".../devquest/core-api:<sha>" in 3.193s
```

**이게 Stage 1의 합격선이다.** 주목할 점:
- **`imagePullSecret`이 없는데도 성공했다** — 노드 IAM 역할에 붙은
  `AmazonEC2ContainerRegistryReadOnly` 정책으로 kubelet이 ECR에 인증한다(`nodes.tf`).
  ECR과 클러스터가 같은 계정·리전이라 가능한 것.
- 166MB를 **3.2초**에 받았다(같은 리전).

### 1-4. CrashLoopBackOff 진단 (의도된 결과)

DB도 시크릿도 없으니 앱은 뜨지 않는다. **원인이 DB일 거라 예상하면 틀린다** —
prod 프로파일은 **로깅 → 빈 생성 → DB** 순으로 초기화되므로, 누락된 환경변수가 **DB보다 먼저** 앱을 죽인다.

```bash
kubectl logs -l app=core-api --tail=50
```

| 순서 | 터지는 곳 | 증상 |
|:--:|---|---|
| ① | **Loki 로깅** | `IllegalArgumentException: URI with undefined scheme` at `Loki4jAppender.start` |
| ② | `JWT_SECRET` | `Could not resolve placeholder 'JWT_SECRET'` |
| ③ | **DB** | `HikariPool-1 - Starting...` → `BeanCreationException: entityManagerFactory` |

> **교훈: "다른 데서 잘 돌던 앱"은 그 플랫폼의 secrets가 가려주던 환경 의존을 갖고 있다.**
> 플랫폼을 옮기면 그게 부팅 순서대로 드러난다. 하나씩 고쳐 재배포하는 두더지잡기보다
> **필수 환경변수를 미리 전수 파악**하는 편이 빠르다.

**전수 파악 명령** (⚠️ 대문자 `${...}`만 훑으면 안 된다):
```bash
grep -rhoE '\$\{[A-Z_][A-Z0-9_]*|source="[A-Z_][A-Z0-9_]*"' be \
  --include='application*.yml' --include='logback*.xml' \
  | grep -oE '[A-Z_][A-Z0-9_]+' | sort -u
```
> 🔴 **변수가 두 가지 형식으로 참조된다** — yml은 `${ENV_VAR}`, logback은 `<springProperty source="ENV_VAR">`.
> **대문자 `${...}`만 찾으면 Loki 변수 3개를 통째로 놓친다** — 실제 인시던트 ①번 원인이었다.
> 이 실수로 "필수 7개"라고 파악했다가 나중에 **실제 10개**임이 드러났다.

**prod 필수 환경변수 10개**:
`DB_HOST` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` · `JWT_SECRET` · `GITHUB_CLIENT_ID` `GITHUB_CLIENT_SECRET`
· `GRAFANA_LOKI_URL` `GRAFANA_LOKI_INSTANCE_ID` `GRAFANA_API_KEY`

### Stage 1 결산 (실측, 2026-07-27)
apply 08:58 ~ destroy 09:18 ≈ **20분, ~$0.05.**
**ECR 이미지는 클러스터를 부숴도 살아남는다**(`0-bootstrap` 레이어 소유) — 다음 세션에 재빌드 불필요.
ECR 저장 비용 ≈ **$0.02/월**(이미지 1개 166MB). 실습 후 남는 유일한 비용.

---

## Stage 2 — RDS 연결 + 시크릿 자동 주입 (IRSA + External Secrets Operator)

**이 단계의 목표**: Stage 1에서 CrashLoop로 끝난 앱을 **코드 변경 0으로** 띄운다.
바뀌는 건 오직 **환경변수를 어디서 가져오는가**뿐이다.

**새로 배우는 것 = IRSA (IAM Roles for Service Accounts).** EKS의 정수다.

> **IRSA가 푸는 문제**: 파드가 AWS API를 부르려면 자격증명이 필요한데,
> ① 액세스 키를 Secret에 넣으면 → 정적 크리덴셜이 클러스터에 상주하고 로테이션이 수동이다.
> ② 노드 IAM 역할을 쓰면 → **같은 노드의 모든 파드가 같은 권한**을 갖는다(경계가 노드 단위).
>
> IRSA는 클러스터의 **OIDC 발급자**가 서명한 ServiceAccount 토큰을 STS가 검증하게 해서,
> 권한 경계를 **노드 → ServiceAccount**로 좁힌다. 정적 키가 아예 없다.

### 2-1. 시크릿 저장 구조 이해 (왜 두 곳을 읽는가)

DB 접속에 필요한 값 4개가 **서로 다른 시크릿에 나뉘어 있다**:

| 값 | 어디에 | 소유자 |
|---|---|---|
| `username` `password` | `rds!db-<uuid>` | **AWS** — `manage_master_user_password = true`로 RDS가 만들고 로테이션 |
| `host` `dbname` | `devquest-eks/db-connection` | 우리(tofu) |

> **왜 갈라져 있나**: AWS가 만드는 마스터 시크릿에는 **접속 좌표(host/dbname)가 안 들어간다.**
> 크리덴셜만 있다. 그래서 좌표는 따로 만들어야 하고, ExternalSecret이 둘을 **합성**한다.

앱 시크릿은 별도로 하나 더 있다(`devquest-eks/app`: JWT + GitHub OAuth + Grafana).

> **왜 소유자별로 쪼갰나**: 자동 로테이션되는 값(AWS 소유)과 수동 값(우리 소유)을 한 덩어리에
> 섞으면, Stage 3에서 DB만 in-cluster로 갈아끼울 때 앱 시크릿까지 건드려야 한다.

> 🔴 **학습 클러스터에 prod 크리덴셜을 넣지 않는다.** GitHub OAuth·Grafana 값은 **자리표시**다.
> 실 OAuth 크리덴셜을 넣으면 **학습 클러스터가 진짜 사용자에게 유효한 토큰을 발급**할 수 있게 된다.
> Stage 2의 목표는 `/health` 200이지 로그인 e2e가 아니다.

### 2-2. External Secrets Operator 설치

```bash
ESO_ROLE=$(tofu -chdir=infra/aws-eks/2-cluster output -raw eso_role_arn)

# 🔴 형식 검사를 지우지 마라 — 아래 설명 참조
# 📌 강제 종료를 쓰지 않는다: 이 블록은 사람이 터미널에 붙여넣는 체크리스트다(SOP §2b와 같은 관례).
#    `exit`을 넣으면 인터랙티브 셸이 닫히고, `return`은 함수 밖이라 실패해 결국 같은 일이 벌어진다.
#    → 🔴가 뜨면 **멈추고 손으로 판단**한다. 다음 명령을 그냥 이어붙이지 말 것.
case "$ESO_ROLE" in
  arn:aws:iam::*:role/*) echo "✅ $ESO_ROLE" ;;
  *) echo "🔴 role ARN이 아니다 — apply가 끝났는지 확인하고 다시 시도. 여기서 멈출 것" ;;
esac

helm repo add external-secrets https://charts.external-secrets.io && helm repo update
helm install external-secrets external-secrets/external-secrets \
  --version 2.8.0 --namespace external-secrets --create-namespace \
  --set installCRDs=true \
  --set "serviceAccount.annotations.eks\.amazonaws\.com/role-arn=$ESO_ROLE" \
  --wait --timeout 5m
```
확인: `STATUS: deployed`, 파드 3개(controller·webhook·cert-controller) 전부 `Running`. ~40초.

> 🔴 **왜 형식 검사가 필요한가 (2026-08-12에 실제로 밟았다).**
> apply가 **아직 안 끝난 상태**에서 `tofu output`을 읽으면 tofu는 이렇게 답한다:
> ```
> Warning: No outputs found
> The state file either has no outputs defined, or all the defined outputs are empty.
> ```
> **그런데 종료코드는 실패가 아니고**, 이 경고 본문이 그대로 `$ESO_ROLE`에 담긴다.
> helm은 그걸 annotation 값으로 순순히 받고, K8s도 항의하지 않는다:
> ```
> eks.amazonaws.com/role-arn: "...Warning: No outputs found..."
> ```
> **IRSA가 안 붙은 채로 ESO가 뜬다.** 그 뒤 SecretStore가 실패하는 것을 보고 나서야 알게 되는데,
> 그때쯤엔 원인이 여기라는 게 안 보인다.
>
> 같은 이유로 **SOP §2b에도 `[ -z "$SHA" ]` 가드**가 있다 — *"aws 호출이 실패한 상황에서
> 가장 위험한 방향으로 조용히 통과한다"*. 그쪽엔 붙였는데 여기엔 없어서 당했다.
> **빈 값·에러 문자열이 설정값 자리에 들어가는 경로는 전부 같은 가드가 필요하다.**

**IRSA가 실제로 붙었는지는 helm 출력이 아니라 파드 안을 본다** (이게 유일하게 믿을 수 있는 확인):
```bash
POD=$(kubectl get pod -n external-secrets -l app.kubernetes.io/name=external-secrets \
  -o jsonpath='{.items[0].metadata.name}')
kubectl get pod -n external-secrets "$POD" \
  -o jsonpath='{range .spec.containers[0].env[*]}{.name}={.value}{"\n"}{end}'
```
```
AWS_ROLE_ARN=arn:aws:iam::<account>:role/devquest-eks-eso
AWS_WEB_IDENTITY_TOKEN_FILE=/var/run/secrets/eks.amazonaws.com/serviceaccount/token
```
> 값이 ARN이 아니면 위 가드를 건너뛴 것이다. 고치는 법:
> ```bash
> helm upgrade external-secrets external-secrets/external-secrets \
>   --version 2.8.0 -n external-secrets --reuse-values \
>   --set "serviceAccount.annotations.eks\.amazonaws\.com/role-arn=$ESO_ROLE" --wait
> kubectl rollout restart deploy -n external-secrets   # SA 주석은 파드 재생성 때만 반영된다
> ```

**IRSA 배선이 실제로 됐는지 파드 안을 본다** (이 단계의 핵심):
```bash
POD=$(kubectl get pod -n external-secrets -l app.kubernetes.io/name=external-secrets \
  -o jsonpath='{.items[0].metadata.name}')
kubectl get pod -n external-secrets $POD \
  -o jsonpath='{range .spec.containers[0].env[*]}{.name}={.value}{"\n"}{end}'
```
확인:
```
AWS_ROLE_ARN=arn:aws:iam::<account>:role/devquest-eks-eso
AWS_WEB_IDENTITY_TOKEN_FILE=/var/run/secrets/eks.amazonaws.com/serviceaccount/token
AWS_REGION=ap-northeast-2
```

> **우리가 한 건 ServiceAccount에 애노테이션 하나 붙인 것뿐이다.** EKS의
> **Pod Identity Webhook**이 파드 생성을 가로채 위 환경변수와 projected 토큰 볼륨(`aws-iam-token`)을
> 자동 주입한다. AWS SDK는 이 두 변수를 보면 자동으로 `AssumeRoleWithWebIdentity`를 쓴다.

**CRD 버전 확인** (매니페스트 `apiVersion`을 정하는 근거):
```bash
kubectl get crd externalsecrets.external-secrets.io \
  -o jsonpath='{range .spec.versions[*]}{.name}{" served="}{.served}{" storage="}{.storage}{"\n"}{end}'
```
확인 (차트 2.8.0 실측):
```
v1       served=true  storage=true
v1beta1  served=false storage=false
```
> ⚠️ **`v1beta1`은 served조차 아니다.** 옛 문서를 보고 `v1beta1`로 쓰면 거부당한다.
> 매니페스트는 **`external-secrets.io/v1`** 을 쓴다.

### 2-3. SecretStore + ExternalSecret 적용

```bash
kubectl apply -f k8s/eso/secretstore.yaml
kubectl apply -f k8s/eso/externalsecret-app.yaml

# db용은 RDS가 만든 마스터 시크릿 ARN을 치환해야 한다(이름을 AWS가 정하므로 코드에 못 박는다)
ARN=$(tofu -chdir=infra/aws-eks/2-cluster output -raw db_master_secret_arn)
sed "s|RDS_MASTER_SECRET_PLACEHOLDER|$ARN|" k8s/eso/externalsecret-db.yaml | kubectl apply -f -
```

> 🔴 **`db_mode=in-cluster`(Stage 3a~)라면 위 db 블록 대신 이걸 쓴다.** 두 파일은 **같은 이름의
> K8s Secret(`core-api-db`)을 만들므로 배타적으로 하나만** apply한다.
> ```bash
> kubectl apply -f k8s/eso/externalsecret-db-incluster.yaml   # 치환 없음
> kubectl apply -f k8s/eso/externalsecret-postgres-tls.yaml   # in-cluster 전용 (TLS)
> ```
> 차이는 **값을 어디서 긁어오는가** 하나뿐이다:
>
> | | Stage 2 (RDS) | Stage 3a~ (in-cluster) |
> |---|---|---|
> | remoteRef 출처 | **2군데** — RDS 관리형 시크릿 + tofu 시크릿 | **1군데** — tofu 시크릿 |
> | 시크릿 이름 | **AWS가 정함** (`rds!db-<uuid>`) | **우리가 정함** (`devquest-eks/db-connection`) |
> | apply 절차 | ARN을 sed로 치환 | **그냥 apply** |
>
> 🔑 *"관리형이 편한 대신 이름을 못 정한다"* 는 트레이드오프가 양쪽에서 드러난다. Stage 2에서
> PLACEHOLDER sed가 귀찮았던 이유가 여기서 사라지는 것으로 증명된다. 반대로 잃은 것도 있다 —
> RDS는 비밀번호를 **자동 로테이션**했지만 in-cluster는 안 한다(그래서 3b-6 수동 동기화가 있다).
>
> ⚠️ 이 분기는 2026-08-12까지 **문서에 없었다.** `externalsecret-db-incluster.yaml`의 주석
> 헤더에만 적혀 있어서, 튜토리얼만 따라가면 in-cluster 경로에서 막혔다.

확인:
```bash
kubectl get secretstore aws-secretsmanager \
  -o jsonpath='{.status.conditions[0].type}={.status.conditions[0].status} {.status.conditions[0].message}{"\n"}'
# → Ready=True store validated       ← IRSA 인증 성공

kubectl get externalsecret
# → 둘 다 STATUS=SecretSynced  READY=True
```

**만들어진 K8s Secret 확인** (값은 절대 출력하지 않는다 — 키 이름만):
```bash
for s in core-api-db core-api-app; do
  echo "=== $s ==="
  kubectl get secret $s -o json | ruby -rjson -e 'puts JSON.parse(STDIN.read)["data"].keys.sort'
done
```
확인: `core-api-db` 4키(DB_HOST/NAME/PASSWORD/USERNAME) + `core-api-app` 3키(JWT_SECRET/GITHUB_CLIENT_ID/GITHUB_CLIENT_SECRET) = **합 7키**.

> ⚠️ **예전 문서는 여기가 "6키 = 합 10키"였다 — 2026-08-07 실측으로 정정.**
> `GRAFANA_*` 3개(LOKI_URL·LOKI_INSTANCE_ID·API_KEY)를 `secrets.tf`에서 **의도적으로 뺐는데**
> (전부 선택값이고, 앱이 `${GRAFANA_API_KEY:}` 기본값으로 없어도 뜬다) 확인 문구가 안 따라왔다.
> 처음 따라 하는 사람은 여기서 "뭔가 잘못됐다"고 판단하고 멈춘다 — **확인 기준이 틀리면
> 없는 실패를 만들어낸다.**

> ✅ **`kubectl create secret`을 한 번도 치지 않았다.** 손으로 만든 Secret은
> ① 누가 언제 넣었는지 기록이 없고 ② 값이 셸 히스토리에 남고 ③ 로테이션되지 않으며
> ④ 클러스터를 재생성할 때마다 사람이 다시 쳐야 한다.


#### 함정 — `SecretSyncedError`가 보일 때 두 층을 구분하라

**IRSA 디버깅의 핵심이다.** 에러 문자열이 어느 층에서 막혔는지 알려준다:

| 에러 | 층 | 원인 |
|---|---|---|
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | **인증** | 신뢰정책의 `sub`/`aud` 불일치. **IRSA 최다 실패 지점** |
| `AccessDeniedException ... no identity-based policy allows` | **인가** | assume는 됐고, 권한 정책이 없거나 리소스 ARN이 안 맞는다 |

후자의 실제 출력:
```
User: arn:aws:sts::<account>:assumed-role/devquest-eks-eso/<session>
  is not authorized to perform: secretsmanager:GetSecretValue on resource: devquest-eks/app
  because no identity-based policy allows the secretsmanager:GetSecretValue action
```
> 주체가 **`assumed-role/...`로 찍혔다는 것 자체가 assume 성공의 증거**다.
> 여기서 신뢰정책을 의심하면 엉뚱한 데를 판다.

`sub` 조건은 이렇게 생겼다 — 파드의 네임스페이스/ServiceAccount와 **한 글자라도** 다르면 인증이 깨진다:
```
<oidc-issuer-host>:sub = system:serviceaccount:external-secrets:external-secrets
<oidc-issuer-host>:aud = sts.amazonaws.com
```
확인:
```bash
aws iam get-role --role-name devquest-eks-eso --query 'Role.AssumeRolePolicyDocument'
```

#### 함정 — 권한을 고쳤는데 계속 실패로 보인다

**ESO의 지수 백오프 때문이다.** 실패 간격이 16s → 32s → 64s → 128s로 벌어져서,
정책을 고친 뒤에도 다음 재시도 차례가 안 와 한동안 `SecretSyncedError`가 남는다.

→ **권한을 의심하기 전에 마지막 시도 시각을 먼저 본다**:
```bash
kubectl describe externalsecret core-api-app | tail -5   # 이벤트의 age 확인
```
→ 즉시 당기려면:
```bash
kubectl annotate externalsecret core-api-app force-sync="$(date +%s)" --overwrite
```

### 2-4. 앱 배포 → `/health` 200 (★ Stage 2 합격선)

```bash
sed "s|IMAGE_PLACEHOLDER|$IMAGE|" k8s/base/core-api.yaml | kubectl apply -f -
kubectl wait --for=condition=Ready pod -l app=core-api --timeout=300s
```

로그 확인:
```bash
kubectl logs -l app=core-api | grep -iE "Database:|migrations|Started"
```
확인 — **빈 RDS에 처음 붙는 것이므로 `applied`여야 한다**:
```
Database: jdbc:postgresql://devquest-eks-db.<...>.ap-northeast-2.rds.amazonaws.com/devquest
          ?sslmode=require (PostgreSQL 17.10)
Successfully applied 13 migrations to schema "public", now at version v13
Started DevQuestApplicationKt in 26.331 seconds
```
- **`sslmode=require`** — 따로 설정하지 않았는데 TLS로 붙는다(RDS 기본).
- 개수 `13`은 `be/support/db-core/src/main/resources/db/migration/`의 `V*.sql` 개수다
  (2026-08-11 기준 V1~V13). **레포가 자라면 이 숫자도 자란다 — 파일 개수와 맞으면 정상.**

> 🔴 **`applied`가 아니라 `validated`가 나왔다면 빈 DB가 아니다.** 파드가 한 번 이상 재기동됐거나
> (첫 기동이 이미 적용했다) 이전 세션의 스키마가 남아 있다는 뜻이다. 둘을 구분 못 하면
> *"마이그레이션이 도는 걸 확인했다"* 고 믿으면서 **실제로는 아무것도 안 돌았을 수 있다.**
>
> ⚠️ 이 자리에는 원래 `Successfully validated 12 migrations`가 *"빈 RDS에 처음부터 실행돼
> 스키마가 만들어진다"* 는 설명과 **나란히** 적혀 있었다(2026-08-11 재현 검증에서 발견).
> 두 문장은 동시에 참일 수 없다 — `validated`는 **이미 적용돼 있었다**는 뜻이다.
> 원본 일지(07-28)를 보면 ECR 태그 실수로 **재배포**한 기록이 바로 위에 있다. 즉 관측된 로그는
> 두 번째 기동의 것이었고, *"처음부터 실행됐다"* 는 **관측이 아니라 추론**이었다.
> 이 구분은 3b-7에서 데이터 영속을 판정하는 **핵심 증거**가 되므로 여기서부터 정확해야 한다.

```bash
kubectl port-forward svc/core-api 8080:8080 &
curl -s localhost:8080/health
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/health/readiness
```
확인:
```json
{"result":"SUCCESS","data":"DevQuest API is running","error":null}
```
```
200
```

> 🔴 **`/health` 200만으로는 "DB에 붙었다"가 증명되지 않는다.** 이 엔드포인트는 **상수 문자열을
> 반환**하며 DB를 보지 않는다(그게 liveness용으로는 옳은 설계다 — 아래 3b-7 참조).
> DB 연결을 증명하는 건 위의 **Flyway 로그**와 `/actuator/health/readiness`다.
> 절 제목의 *"`/health` 200 = 합격선"* 은 편의상의 이름이고, **판정 근거는 로그 쪽**이다.

> 🎉 **Stage 1과 같은 이미지가 코드 변경 0으로 떴다.** 바뀐 건 환경변수 주입 경로뿐이다.
> `application-prod.yml`을 100% 환경변수 기반으로 유지한 설계가 여기서 배당금을 낸다 —
> Neon ↔ RDS ↔ in-cluster Postgres를 **재빌드 없이** 갈아끼울 수 있다.

**`DB_HOST`에 포트가 없어야 한다**(설계 확인):
```bash
kubectl get secret core-api-db -o jsonpath='{.data.DB_HOST}' | base64 -d
# → devquest-eks-db.<...>.rds.amazonaws.com     (:5432 없음)
```
> jdbc-url이 `jdbc:postgresql://${DB_HOST}/${DB_NAME}` 형태라 포트가 붙으면 URL이 깨진다.
> 그래서 Terraform에서 RDS의 **`.address`**(포트 없음)를 쓴다. **`.endpoint`는 `host:5432`**라 틀린다.

### 2-5. teardown — 순서가 중요하다

```bash
# ① ESO가 소유한 것부터
kubectl delete externalsecret --all -A
kubectl delete secretstore --all -A

# ② 워크로드
kubectl delete -f k8s/base/ --ignore-not-found

# ③ 인프라 (Stage 0-4·0-5와 동일)
tofu -chdir=infra/aws-eks/2-cluster destroy -auto-approve
```

> 🔴 **`kubectl delete secret core-api-db`로 지우면 안 된다.** ExternalSecret이
> `creationPolicy: Owner`라 **Secret이 즉시 되살아난다.** 실측:
> ```
> 삭제 전 UID: 94fe931e-42d4-4a3f-9715-8337b3266142
> 8초 후 UID : db35e78b-2fa5-44e6-9b13-d2eda253580e     ← 같은 이름의 다른 객체
> ```
> 반대로 **소유자인 ExternalSecret을 지우면 K8s Secret도 함께 GC된다.**
> ESO 자체(Helm 릴리스)는 클러스터와 함께 사라지므로 따로 지울 필요 없다.

고아 전수 검증은 **Stage 0-5와 동일**하되, RDS·Secrets Manager 항목을 반드시 포함할 것.

### Stage 2 결산 (실측, 2026-07-28)

과금 구간 = apply 시작 ~ destroy 종료 = **26분 35초**

| 리소스 | 생존 | 단가 | 소계 |
|---|---|---|---|
| EKS 컨트롤플레인 | ~27분 | $0.10/h | $0.045 |
| t4g.small On-Demand ×1 | ~24분 | $0.0208/h | $0.008 |
| RDS db.t4g.micro | ~15분 | $0.025/h | $0.006 |
| gp3 스토리지·시크릿 | — | 월정액 일할 | <$0.002 |
| **합계** | | | **≈ $0.06** |

> **RDS를 넣어도 시간당 증분은 $0.028(18%)뿐이다.** 여전히 비용의 **65%가 컨트롤플레인**이다.
> → destroy-after-use에서 아낄 대상은 "어떤 리소스를 쓰느냐"가 아니라 **"몇 분 켜두느냐"**다.

---

## Stage 3a — RDS를 in-cluster Postgres로 갈아끼우기 (EBS CSI + 동적 PVC)

**목표**: `kubectl get pvc`가 `Bound`가 되고, 그때 AWS에 EBS가 **자동으로** 생겨 있는 것을 확인한다.
그리고 파드를 죽여도 데이터가 남는 것까지.

**Stage 2와 바뀌는 것은 DB 하나뿐이다.** 앱 이미지는 같은 sha를 쓴다 —
`application-prod.yml`이 100% 환경변수 기반이라 코드를 안 고치고 갈아끼울 수 있기 때문이다.
(단 한 군데 예외가 있는데, 그게 3a-5의 발견이다.)

### 3a-0. 개념 — PV / PVC / StorageClass가 각각 뭔가

처음 보면 셋이 왜 필요한지 헷갈린다. **역할이 다르다**:

| | 무엇 | 비유 |
|---|---|---|
| **PVC** (PersistentVolumeClaim) | "10GiB짜리 디스크 하나 주세요" — **요청서** | 주문서 |
| **PV** (PersistentVolume) | 실제로 배정된 디스크 — **자원** | 배송된 물건 |
| **StorageClass** | 요청이 오면 **어떻게 만들지**의 규칙 | 주문 처리 방침 |

정적(static) 방식은 관리자가 PV를 미리 만들어 두고 PVC가 그중 맞는 걸 골라 쓴다.
**동적(dynamic) 방식**은 PVC가 오면 StorageClass가 PV를 **그 자리에서 만든다.** 3a는 동적이다.

그 "만드는" 일을 실제로 하는 게 **CSI 드라이버**다. K8s 자체는 EBS가 뭔지 모른다 —
CSI(Container Storage Interface)는 K8s가 스토리지 벤더 코드를 코어에서 걷어내고 표준 인터페이스로
뺀 결과물이라, EBS·EFS·GCP PD가 전부 같은 방식으로 붙는다.

### 3a-1. EBS CSI 드라이버 — 애드온만 추가하면 안 된다

```bash
aws eks describe-addon-versions --addon-name aws-ebs-csi-driver --region ap-northeast-2 \
  --query 'addons[0].addonVersions[0].[addonVersion,requiresIamPermissions]' --output text
```
기대 출력:
```
v1.63.1-eksbuild.1      True
```

🔴 **`requiresIamPermissions: True`** — 이게 핵심이다. 이 드라이버는 값을 읽기만 하는 게 아니라
**AWS 리소스를 만들고 지운다**(CreateVolume/DeleteVolume/AttachVolume). 권한 없이 애드온만 달면
PVC가 영원히 `Pending`이다.

그래서 IRSA 역할이 함께 필요하다(`infra/aws-eks/2-cluster/irsa-ebs-csi.tf`).
Stage 2의 ESO IRSA와 **구조는 같고 의미가 다르다**:

| | ESO (Stage 2) | EBS CSI (Stage 3a) |
|---|---|---|
| 하는 일 | AWS에서 **값을 읽는다** | AWS **리소스를 만들고 지운다** |
| 실패하면 | 앱이 못 뜬다 | 인프라가 안 생긴다 + **고아 과금의 발원지** |
| 권한 범위 | 시크릿 ARN 3개로 한정 | 관리형 정책 `AmazonEBSCSIDriverPolicy` |

권한 범위가 다른 이유: 아직 만들어지지 않은 볼륨의 ARN을 미리 알 수 없다(동적 프로비저닝의 본질).
그래서 이 정책은 **리소스가 아니라 태그로 경계를 긋는다** — `aws:RequestTag/ebs.csi.aws.com/cluster`.
"CSI가 만든 볼륨만 CSI가 지운다". 최소권한을 ARN으로 못 그을 때 쓰는 실례다.

### 3a-2. 🔴 apply 전에 파드 상한을 계산한다

**이 단계를 건너뛰면 apply는 성공하는데 파드가 Pending에 갇힌다.**

```bash
# 애드온의 기본 설정값을 apply 전에 조회한다
aws eks describe-addon-configuration --addon-name aws-ebs-csi-driver \
  --addon-version v1.63.1-eksbuild.1 --region ap-northeast-2 \
  --query 'configurationSchema' --output text | grep -o '"replicaCount"[^}]*}'
```
기대 출력:
```
"replicaCount":{"default":2,"description":"Number of replicas in the controller Deployment","minimum":1,...}
```

t4g.small의 파드 상한은 **11** (= ENI 3 × (IP 4 − 1) + 2). 계산해 보면:

| 무엇 | 개수 |
|---|---|
| 시스템 (aws-node·kube-proxy·coredns ×2) | 4 |
| ebs-csi-controller **(기본값 2)** + ebs-csi-node | **3** |
| ESO (controller·webhook·cert-controller) | 3 |
| postgres + core-api | 2 |
| **합계** | **12 > 11** ❌ |

→ `configuration_values`로 컨트롤러를 1개로 낮춘다. 노드 1대짜리 학습 클러스터에서
컨트롤러 2개는 어차피 HA가 아니다(같은 노드에 떠서 함께 죽는다).

```hcl
resource "aws_eks_addon" "ebs_csi" {
  cluster_name             = aws_eks_cluster.main.name
  addon_name               = "aws-ebs-csi-driver"
  service_account_role_arn = aws_iam_role.ebs_csi.arn
  configuration_values     = jsonencode({ controller = { replicaCount = 1 } })
  depends_on               = [aws_eks_node_group.main]
}
```

> 💡 **왜 이 조회가 중요한가**: `tofu plan`은 **자기 코드만 본다.** AWS 쪽 기본값·제약·한도는
> apply해야 드러난다. 이 프로젝트에서 그렇게 깨진 적이 세 번 있었다(한글 description /
> EMAIL+IMMEDIATE 조합 / 계정당 모니터 한도). **apply 전에 `aws ... describe-*`로 실물을 조회하는 것**이
> 유일한 예방책이다.

### 3a-3. StorageClass — 두 줄이 전부를 결정한다

```yaml
provisioner: ebs.csi.aws.com
parameters:
  type: gp3
  encrypted: "true"
reclaimPolicy: Delete
volumeBindingMode: WaitForFirstConsumer
```

**`volumeBindingMode`**
- `Immediate` = PVC를 만들자마자 EBS 생성. **AZ가 아무 데나 정해진다.**
- `WaitForFirstConsumer` = 파드가 스케줄될 때까지 기다렸다가 **그 노드와 같은 AZ에** 만든다.

🔴 EBS는 AZ에 묶인 리소스라 다른 AZ 노드에 붙일 수 없다. 노드그룹이 여러 서브넷에 걸쳐 있으면
`Immediate`는 **운 나쁜 날에만 터지는** 버그가 된다. `WaitForFirstConsumer`가 이걸 원천 제거한다.

**`reclaimPolicy`** — PVC를 지웠을 때 EBS를 어떻게 할지.
`Delete`면 함께 삭제된다. 학습 클러스터에서는 **이게 맞다** — `Retain`으로 두면
destroy 후에도 볼륨이 남아 조용히 과금된다.

### 3a-4. Postgres StatefulSet — 두 개의 함정

```bash
kubectl apply -f k8s/base/postgres.yaml
kubectl get pvc          # 처음엔 Pending이 정상이다 (WaitForFirstConsumer)
```

**함정 ① `lost+found`** — EBS를 ext4로 포맷하면 마운트 지점에 이 디렉토리가 생긴다.
postgres 엔트리포인트는 PGDATA가 "비어 있을 때만" initdb를 돌리므로, 비어있지 않다고 판단해
건너뛴 뒤 PG_VERSION이 없다며 죽는다. → **PGDATA를 하위 디렉토리로 내린다**:
```yaml
- name: PGDATA
  value: /var/lib/postgresql/data/pgdata   # mountPath의 하위
```

**함정 ② 프로브에서 `$(VAR)`는 치환되지 않는다.** K8s의 변수 확장은 컨테이너의
`command`/`args`/`env.value`에만 적용되고 **프로브의 exec에는 적용되지 않는다.**
exec은 셸을 거치지 않으므로 문자열이 그대로 넘어간다. 더 나쁜 건 `pg_isready`가 인증을 하지 않아
엉뚱한 사용자명으로도 0을 반환할 수 있다는 점 — **틀린 채로 통과하는 프로브**가 된다.
```yaml
command: ["sh", "-c", 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
```

### 3a-5. 🔴 관리형이 공짜로 주던 것 — TLS

여기서 앱이 죽는다:
```
Caused by: org.postgresql.util.PSQLException: The server does not support SSL.
```

원인은 `application-prod.yml`이다:
```yaml
jdbc-url: jdbc:postgresql://${DB_HOST}/${DB_NAME}?sslmode=require
```
호스트·DB명·계정은 전부 환경변수인데 **`sslmode=require`만 상수**다.
RDS는 TLS가 켜진 채로 오기 때문에 그동안 이게 드러나지 않았다.

> 🔑 **이게 Stage 3의 진짜 교훈이다.** 관리형에서 자체운영으로 옮길 때 사라지는 것은
> 자동 백업·PITR처럼 **눈에 띄는 기능**만이 아니다. **아무도 언급하지 않는 기본값**이 함께 사라진다.
> 그리고 그건 문서를 읽어서가 아니라 **실제로 붙여봐야** 드러난다.

인증서는 **손으로 만들지 않는다.** tofu가 만들어 Stage 2의 Secrets Manager → ESO 파이프로 흘린다
(`infra/aws-eks/2-cluster/postgres-tls.tf`) — 새 개념이 하나도 안 늘고 세션마다 자동 재생성된다.
`postgres:17-alpine`에는 openssl이 없어서 initContainer 방식은 쓸 수 없다(실측).

**자기서명으로 충분한 이유**: `sslmode=require`는 **암호화만 요구하고 인증서 검증을 하지 않는다.**
검증하려면 `verify-ca`/`verify-full`이어야 하고 그때는 CA를 클라이언트에 심어야 한다.
⚠️ 즉 **이 구성은 MITM을 막지 못한다.** 실운영이라면 cert-manager로 CA 체계를 세울 자리다.

**파일 권한 함정** — PostgreSQL은 키 파일이 group/world에 열려 있으면 기동을 거부한다.
예외가 하나 있는데 **"root 소유 + group read(0640)"** 다. K8s Secret 볼륨은 kubelet이
`root:fsGroup` 소유로 마운트하므로 **0600(=postgres 소유)을 만들 수 없다.** 그래서:
```yaml
securityContext:
  fsGroup: 70            # postgres:17-alpine의 postgres uid
volumes:
  - name: certs
    secret:
      secretName: postgres-tls
      defaultMode: 0640  # ⚠️ 앞의 0을 빼면 10진수 640으로 읽힌다
```
> ⚠️ **`fsGroup: 70`은 이미지에 결합된 값이다.** postgres 이미지 태그를 올릴 때 반드시 함께 확인한다:
> ```bash
> kubectl exec postgres-0 -- id postgres     # uid=70(postgres) 이어야 한다
> ```
> uid가 바뀌면 키 파일의 group이 어긋나 PG가 기동을 거부한다
> (`FATAL: private key file "/certs/server.key" has group or world access`).
> alpine ↔ debian 계열 사이에서도 다를 수 있다.

확인:
```bash
kubectl exec postgres-0 -- sh -c 'ls -l /certs/..data/; psql -U "$POSTGRES_USER" -tAc "SHOW ssl;"'
```
기대 출력:
```
-rw-r-----  1 root  postgres  1294  server.crt
-rw-r-----  1 root  postgres  1675  server.key
on
```

### 3a-6. 검증 — 동적 프로비저닝이 실제로 일어났나

```bash
kubectl describe pvc data-postgres-0 | tail -6
```
기대 출력(이 4줄이 전 과정이다):
```
Normal  WaitForFirstConsumer   waiting for first consumer to be created before binding
Normal  Provisioning           ebs.csi.aws.com_ebs-csi-controller-...
Normal  ExternalProvisioning   Waiting for a volume to be created either by the external provisioner ...
Normal  ProvisioningSucceeded  Successfully provisioned volume pvc-09b2533e-...
```

AWS 쪽 실물 확인:
```bash
aws ec2 describe-volumes --region ap-northeast-2 \
  --filters "Name=tag:kubernetes.io/created-for/pvc/name,Values=data-postgres-0" \
  --query 'Volumes[].[VolumeId,Size,VolumeType,AvailabilityZone,Encrypted,State]' --output table
```
기대: 10 / gp3 / **노드와 같은 AZ** / Encrypted `True` / `in-use`

앱까지:
```bash
kubectl port-forward svc/core-api 8080:8080 &
curl -s localhost:8080/health     # {"result":"SUCCESS","data":"DevQuest API is running","error":null}
```

**★ 이 Stage의 합격선** — 파드를 죽여도 데이터가 남는가:
```bash
kubectl exec postgres-0 -- psql -U devquest -d devquest -tAc "select count(*) from tech_question_bank;"
kubectl get pod postgres-0 -o jsonpath='{.metadata.uid}'; echo
kubectl delete pod postgres-0        # 강제로 죽인다
# 재생성 대기 후
kubectl get pod postgres-0 -o jsonpath='{.metadata.uid}'; echo    # UID가 달라야 한다
kubectl get pvc data-postgres-0 -o jsonpath='{.spec.volumeName}'; echo  # 볼륨은 같아야 한다
kubectl exec postgres-0 -- psql -U devquest -d devquest -tAc "select count(*) from tech_question_bank;"
```
기대: **UID는 바뀌고, 볼륨과 행 수는 그대로.** Deployment였다면 보장되지 않는다.

### 3a-7. teardown — 🔴 순서가 돈이다

```bash
kubectl delete externalsecret --all -A     # ① 소유자부터 (Secret이 부활하지 않게)
kubectl delete secretstore --all -A
kubectl delete -f k8s/base/ --ignore-not-found   # ② 워크로드
```

여기서 **멈추고 확인해 본다**:
```bash
aws ec2 describe-volumes --region ap-northeast-2 \
  --filters "Name=tag:ebs.csi.aws.com/cluster,Values=true" --query 'Volumes[].[VolumeId,State]' --output text
```
```
vol-0c32788ebc4a95cb0   in-use     ← StatefulSet을 지웠는데 EBS는 살아있다
```

🔴 **StatefulSet 삭제 ≠ 볼륨 삭제.** `volumeClaimTemplates`가 만든 PVC는 데이터 보호를 위해
의도적으로 남는다. 그리고 이 볼륨은 **tofu state 밖**이라(`tofu state list`에 `aws_ebs_volume` 0건)
`tofu destroy`가 못 지운다. **여기서 끝내면 월 $0.0912/GB가 영원히 나간다.**

```bash
kubectl delete pvc --all -A        # ③ 🔴 이게 있어야 EBS가 회수된다
# 7초 내에 사라지는 것을 확인
cd infra/aws-eks/2-cluster && tofu destroy    # ④ 인프라
```

### Stage 3a 결산 (실측, 2026-07-30)

과금 구간 = **약 27분** (15:17:58 → 15:45)

| 단계 | 실측 |
|---|---|
| 컨트롤플레인 생성 | 5분 48초 |
| 노드그룹 | 1분 57초 |
| EBS CSI 애드온 | 35초 |
| PVC Pending → Bound | **11초** |
| PVC 삭제 → EBS 회수 | **7초** |
| Flyway 12개 마이그레이션 | 0.161초 |
| **합계 비용** | **≈ $0.06** ($0.13/h) |

| | Stage 2 (RDS) | Stage 3a (in-cluster) |
|---|---|---|
| DB 생성 시간 | 4분 50초 | **즉시** (이미지 pull만) |
| 시간당 단가 | $0.155 | **$0.13** |
| 시크릿 출처 | 2군데 (AWS 소유 + tofu) | **1군데** (전부 tofu) |
| apply 절차 | ARN을 `sed`로 치환 | **치환 없음** |
| TLS | 공짜 | **직접 발급·마운트** |
| 백업·PITR·로테이션 | 관리형 제공 | **없음** |

> **결론**: 자체운영이 더 싸고 빠르지만, **관리형이 조용히 해주던 일**(TLS·백업·로테이션)을
> 전부 떠안는다. 학습 클러스터에서는 맞는 거래고, prod에서는 대체로 아니다.

---

## Stage 3b — 영속 EBS + static PV (부수고 다시 지어도 데이터가 붙는가)

3a에서는 **PVC가 볼륨을 만들었다.** 세션이 끝나면 볼륨도 같이 사라진다 — 학습에는 편하지만
*"클러스터를 재생성해도 데이터가 살아남는가"* 는 확인할 수 없다. 3b는 그 반대로 간다:
**terraform이 볼륨을 소유하고, K8s는 이미 있는 볼륨을 빌려 쓴다.**

> ⚠️ **3a의 정답이 3b의 함정이 된다.** `reclaimPolicy: Delete`는 3a에서 고아 볼륨을 막는
> 장치였지만 3b에서는 **6개월치 데이터를 지우는 사고**가 된다. `volumeClaimTemplates`도
> 마찬가지로 static PV와 충돌한다. **같은 설정의 옳고 그름이 목적에 따라 뒤집히는 것**이
> 이 단계의 핵심이다.

### 3b-0. 개념 — 동적과 정적의 차이는 "누가 만드는가"

```
[3a 동적]  PVC "10Gi 주세요"  →  StorageClass(ebs.csi.aws.com)  →  CSI가 EBS를 생성
                                                                   → PV 자동 생성
[3b 정적]  terraform이 EBS 생성  →  사람이 PV를 작성(volumeHandle에 볼륨 ID를 직접 적음)
                                  →  PVC가 그 PV를 지목(volumeName)
```

`PV`는 **실물 볼륨을 K8s에 소개하는 객체**이고, `PVC`는 **주문서**다. 3a에서는 주문서를 내면
창고가 알아서 물건을 만들어 왔고, 3b에서는 **이미 있는 물건의 창고증(PV)을 사람이 써 준다.**

### 3b-1. 볼륨은 `0-bootstrap`에 만든다 — `2-cluster`가 아니다 (💰 여기서 영속 과금 시작)

```hcl
# infra/aws-eks/0-bootstrap/ebs-postgres.tf
resource "aws_ebs_volume" "postgres_data" {
  count             = var.postgres_persistent_volume_enabled ? 1 : 0
  availability_zone = var.persistent_az     # ← 이 리소스의 정체성
  size              = var.postgres_volume_size_gb
  type              = "gp3"
  encrypted         = true
  lifecycle { prevent_destroy = true }
}
```

<!-- verify: infra/aws-eks/0-bootstrap/ebs-postgres.tf ~ aws_ebs_volume" "postgres_data -->

🔴 **왜 `2-cluster`가 아닌가.** 이 레포는 세션이 끝나도 클러스터가 살아있으면
`eks-reaper.sh`(dead man's switch)가 `tofu destroy -auto-approve`를 돈다.
볼륨을 `2-cluster`에 뒀다면 **리퍼가 6개월치 데이터를 자동으로 지웠을 것이다.**

🔴 **`prevent_destroy`만으로는 답이 안 된다.** 그걸 `2-cluster`에 걸면 destroy **전체**가
plan 단계에서 거부돼 EKS·노드까지 아무것도 안 지워지고, 리퍼가 30분마다 같은 에러를 반복한다
— **안전장치가 벽돌이 된다.** (`local_file` 3개짜리 임시 프로젝트로 $0에 재현해 확인했다.)

→ 레이어를 나누는 것이 답이다. 리퍼가 destroy하는 대상은 `2-cluster`뿐이다
(`eks-session-marker.sh`가 마커에 그 경로를 박는다).

```bash
cd infra/aws-eks/0-bootstrap && tofu apply
aws ec2 describe-volumes --region ap-northeast-2 \
  --filters "Name=tag:Persistent,Values=true" \
  --query 'Volumes[].[VolumeId,State,Size,AvailabilityZone]' --output text
```
```
vol-0518b6d0dcd2b0d70   available   10   ap-northeast-2a
```

> 💰 **이 순간부터 10 GiB gp3 = 약 $0.91/월이 클러스터와 무관하게 나간다.**
> 세션을 끝내도, `2-cluster`를 destroy해도 남는다. 그게 이 단계의 목적이다.

### 3b-2. 노드 AZ를 볼륨과 같은 AZ로 고정한다

**EBS는 특정 AZ의 스토리지에 물리적으로 존재하고 다른 AZ의 인스턴스에는 붙지 않는다.**
3a에서는 `volumeBindingMode: WaitForFirstConsumer`가 이 문제를 가려주고 있었다 —
파드가 스케줄된 뒤에 그 AZ에 볼륨을 만들었으니 항상 맞았다. 3b에서는 볼륨이 먼저 있으므로
**노드가 그 AZ로 가야 한다.** 안 맞으면 파드가 영구 `Pending`이다.

`2-cluster`가 `0-bootstrap`의 값을 remote state로 읽어 서브넷을 하나로 좁힌다:

```hcl
# infra/aws-eks/2-cluster/nodes.tf
subnet_ids = [
  data.terraform_remote_state.network.outputs.public_subnet_ids_by_az[
    data.terraform_remote_state.bootstrap.outputs.persistent_az
  ]
]
```

> 🔑 AZ 값은 **`0-bootstrap`의 `var.persistent_az` 한 곳에만** 존재한다. 2-cluster에 같은 변수를
> 또 만들지 말 것 — 같은 사실을 두 곳에 저장하면 한쪽이 썩는다.

확인:
```bash
kubectl get nodes -L topology.kubernetes.io/zone --no-headers | awk '{print $1, $6}'
```
```
ip-10-0-8-95.ap-northeast-2.compute.internal ap-northeast-2a     ← 볼륨과 같은 AZ
```

### 3b-3. 비밀번호도 `0-bootstrap`에 둔다 — **볼륨과 수명이 같아야 하는 것은 볼륨과 같은 레이어에**

이건 08-07 검증에서 **실제로 터진 뒤에** 알게 된 것이다(원장 `L-14`).

```hcl
# infra/aws-eks/0-bootstrap/postgres-password.tf
resource "random_password" "postgres_master" {
  length  = 32
  special = false          # JDBC URL·psql·base64를 오가며 이스케이프 사고 원천 차단
  lifecycle { prevent_destroy = true }
}
```

<!-- verify: infra/aws-eks/0-bootstrap/postgres-password.tf ~ random_password" "postgres_master -->

**왜 필요한가.** postgres 이미지는 `POSTGRES_PASSWORD`를 **`initdb` 시점에만** 쓴다.
데이터 디렉토리가 이미 있으면 그 변수를 **읽지도 않고** 옛 해시를 유지한다. 그런데 비밀번호를
`2-cluster`에 두면 destroy가 state에서 지우고, 재apply가 **새 값**을 만들어 앱에 흘린다:

```
볼륨   → initdb 때의 옛 비밀번호   (안 바뀜)
tfstate → 재apply의 새 비밀번호     (바뀜)
결과   → FATAL:  password authentication failed for user "devquest"
```

**데이터는 완벽히 살아남는데 자격증명만 안 붙는다.** 볼륨을 `0-bootstrap`에 둔 것과 **같은 논리**가
비밀번호에도 적용된다 — 영속 볼륨을 도입하는 순간 **볼륨 안에 구워지는 모든 것**이 같은 제약을 받는다.

> ⚠️ `lifecycle { ignore_changes = all }`로는 못 고친다. `ignore_changes`는 *state에 있는 값*과
> 설정을 비교해 diff를 무시하는 것이라, destroy가 state에서 리소스를 지우면 무시할 대상 자체가 없다.

### 3b-4. 파드 상한을 먼저 확보한다

`vpc-cni`는 파드에 VPC IP를 직접 주므로 **파드 수가 인스턴스의 ENI 수에 묶인다.**
t4g.small은 상한 11이고, 3a 시점에 이미 11/11로 여유가 0이었다.

3b에서 두 가지를 적용해 해소했다(t4g.medium 증설은 불필요해 채택하지 않았다 — $0.13/h 유지):
<!-- verify: infra/aws-eks/2-cluster/addons.tf ~ replicaCount --> <!-- verify: k8s/base/core-api.yaml ~ type:[[:space:]]*Recreate -->

```hcl
# infra/aws-eks/2-cluster/addons.tf — 노드가 1대라 coredns replica 2는 HA가 아니다
configuration_values = jsonencode({ replicaCount = 1 })
```
```yaml
# k8s/base/core-api.yaml — RollingUpdate는 새 파드를 먼저 띄우므로 여분 슬롯이 필요하다
strategy:
  type: Recreate
```

> 이걸 안 하면 `kubectl rollout restart`가 이렇게 실패한다:
> `0/1 nodes are available: 1 Insufficient memory, 1 Too many pods`

### 3b-5. static PV 3종 적용 — 3a와 달리 **치환이 필요하다**

```bash
cd infra/aws-eks/2-cluster
sed -e "s|EBS_VOLUME_ID_PLACEHOLDER|$(tofu output -raw postgres_data_volume_id)|" \
    -e "s|PERSISTENT_AZ_PLACEHOLDER|$(tofu output -raw persistent_az)|" \
    ../../../k8s/base/postgres-static.yaml | kubectl apply -f -
```
```
storageclass.storage.k8s.io/gp3-static created
persistentvolume/postgres-data created
persistentvolumeclaim/postgres-data created
service/postgres created
statefulset.apps/postgres created
```

> 🔑 볼륨 ID는 세션이 바뀌어도 같지만(영속) **계정마다 다르고, 퍼블릭 레포에 리소스 ID를 박지 않는다.**
> `core-api.yaml`의 `IMAGE_PLACEHOLDER`와 같은 관례다.

세 객체가 각각 무엇을 하는지:

| | 값 | 왜 |
|---|---|---|
| **StorageClass** | `provisioner: kubernetes.io/no-provisioner`<br>`volumeBindingMode: WaitForFirstConsumer` | **동적 프로비저닝을 끄기 위해** 존재한다. 이름만 필요하고 아무것도 만들지 않는다 <!-- verify: k8s/base/postgres-static.yaml ~ no-provisioner --> |
| **PV** | `csi.volumeHandle: <볼륨 ID>`<br>`persistentVolumeReclaimPolicy: Retain` | 실물 EBS의 창고증. 🔴 **`Retain`이 3a의 `Delete`를 뒤집는 지점** <!-- verify: k8s/base/postgres-static.yaml ~ persistentVolumeReclaimPolicy:[[:space:]]*Retain --> |
| **PVC** | `volumeName: postgres-data` | 그 PV를 **지목**한다(아무거나 받지 않는다) |

🔴 **StatefulSet에 `volumeClaimTemplates`가 없다.** 3a에서는 그게 파드마다 PVC를 자동 생성했지만,
static PV에서는 PVC를 사람이 만들었으므로 충돌한다. 게다가 `volumeClaimTemplates`는 **불변 필드**라
살아 있는 StatefulSet에서 고쳐 apply하면 거부된다 — 지우고 다시 만들어야 한다.

바인딩 확인:
```bash
kubectl get pv postgres-data ; kubectl get pvc postgres-data
```
```
NAME            CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM
postgres-data   10Gi       RWO            Retain           Bound    default/postgres-data
```

**볼륨이 실제로 붙었는지는 AWS에 직접 묻는다** (K8s의 말이 아니라 최종 상태를 본다):
```bash
aws ec2 describe-volumes --region ap-northeast-2 --volume-ids <볼륨 ID> \
  --query 'Volumes[0].[VolumeId,State,Attachments[0].InstanceId,Attachments[0].Device]' --output text
```
```
vol-0518b6d0dcd2b0d70   in-use   i-02df4703dfb088463   /dev/xvdaa
```

### 3b-6. 🔴 비밀번호 동기화 — 파드가 Ready된 직후 **매번**

```bash
PW=$(kubectl get secret core-api-db -o jsonpath='{.data.DB_PASSWORD}' | base64 -d)
kubectl exec postgres-0 -- psql -U devquest -d devquest \
  -c "ALTER USER devquest PASSWORD '$PW';"
```
```
ALTER ROLE
```

3b-3에서 비밀번호를 영속화했어도 이 단계는 남는다. **그 이전에 구워진 볼륨은 어느 state에도 없는
옛 비밀번호를 들고 있고**, 앞으로 비밀번호를 회전시킬 때도 같은 갈라짐이 생기기 때문이다.
같은 값으로 다시 걸면 무의미하므로 **멱등**이고 2초면 끝난다.

> *"한 번만 하면 되는 수동 절차"* 로 두지 않은 이유: 그런 건 반드시 잊히고, **잊힌 걸 과금 중에
> 알게 된다**(실제로 원인 파악에 7분을 태웠다).
> 컨테이너 안 로컬 소켓은 `trust` 인증이라 **옛 비밀번호 없이도** 이 명령이 통한다.

#### 🔴 동기화됐는지 확인할 때 `-h 127.0.0.1`을 쓰면 항상 통과한다

`pg_hba.conf` 실측(2026-08-12):

<!-- verify: docs/eks-session-sop.md ~ status\.podIP -->
<!-- 🔑 마커를 `~ scram-sha-256`으로 걸었다가 되돌렸다(08-12 QA F-5). 그 문자열은 SOP의
     **코드펜스에도** 있어서, 경고 산문을 통째로 지워도 펜스만 남으면 통과한다 —
     주장("파드 IP로 검증하라")이 아니라 근처 단어를 검사하는 장식 마커였다.
     `status.podIP`는 그 경고 블록의 **대체 명령 안에만** 있으므로 블록이 사라지면 같이 사라진다. -->

```
local  all all                trust
host   all all 127.0.0.1/32   trust          ← 소켓뿐 아니라 루프백 TCP도 trust
host   all all ::1/128        trust
host   all all all            scram-sha-256  ← 비밀번호를 실제로 검사하는 유일한 줄
```

*"소켓은 trust"* 까지만 알고 **"그러므로 TCP는 검사된다"** 로 추론하면 틀린다. 08-12 검증 세션에서
실제로 밟았다 — `psql -h 127.0.0.1`로 확인했더니 **동기화 전인데도 성공**했고, 하마터면
"갈라짐이 없다"고 결론 낼 뻔했다. 검사가 주장(비밀번호가 맞는가)보다 헐거운 대리물
(TCP로 붙는가)을 보고 있었다.

검증은 **파드 IP나 다른 파드에서** 붙어야 `scram-sha-256` 줄을 탄다:

```bash
PODIP=$(kubectl get pod postgres-0 -o jsonpath='{.status.podIP}')
kubectl get secret core-api-db -o jsonpath='{.data.DB_PASSWORD}' | base64 -d | \
  kubectl exec -i postgres-0 -- sh -c "read -r PW; PGPASSWORD=\"\$PW\" \
    psql -h $PODIP -U devquest -d devquest -tAc 'select 1'"
```

| 출력 | 뜻 |
|---|---|
| `1` | 동기화 성공 |
| `FATAL: password authentication failed for user "devquest"` | 아직 갈라져 있다 — 위 `ALTER USER`를 실행 |

> 💡 비밀번호를 **stdin으로 넘긴다.** 명령줄 인자로 주면 `ps`와 셸 히스토리에 남는다.

### 3b-7. 앱 기동 확인

```bash
kubectl logs <core-api 파드> | grep -E "Successfully (applied|validated)|Started DevQuest"
kubectl exec <core-api 파드> -- sh -c 'wget -qO- http://localhost:8080/health'
```

**첫 부팅**(빈 볼륨):
```
Successfully applied 13 migrations to schema "public", now at version v13
{"result":"SUCCESS","data":"DevQuest API is running","error":null}
```

**재구축 후**(데이터가 있는 볼륨) — `applied`가 아니라 **`validated`** 다:
```
Successfully validated 13 migrations (execution time 00:00.013s)
Current version of schema "public": 13
```

> 🔑 이 차이가 *"스키마가 볼륨에 살아 있다"* 의 증거다.

#### 🔴 `/health` 200은 "앱이 살아났다"를 증명하지 않는다 — probe 두 개를 구분한다

위 `wget /health`는 **상수 문자열을 반환**한다. DB가 죽어 있어도 200이다. 그래서 파드가 `Ready`인
것만 보고 넘어가면, **DB에 못 붙은 앱에 트래픽이 흘러들어 전부 500**이 되는 상태를 통과시킨다.
`k8s/base/core-api.yaml`은 두 probe를 **일부러 다른 엔드포인트로** 나눠 둔다:

| probe | 경로 | 무엇에 답하나 | 실패 시 |
|---|---|---|---|
| **liveness** | `/health` (상수) | *"프로세스가 교착·데드락에 빠졌나"* | **재시작** |
| **readiness** | `/actuator/health/readiness` (`db`,`ping`) | *"지금 트래픽을 받아도 되나"* | Service 엔드포인트에서 **제외**(재시작 안 함) |

<!-- verify: k8s/base/core-api.yaml ~ path:[[:space:]]*/actuator/health/readiness -->
<!-- verify: be/core/core-api/src/main/resources/application.yml ~ include:[[:space:]]*db,ping[[:space:]]*$ -->

🔴 **liveness가 DB를 보면 안 된다.** DB가 잠깐 흔들릴 때 **모든 파드가 동시에 재시작**하고,
그 재시작 폭풍이 DB를 더 밀어붙여 **장애를 복구하는 게 아니라 증폭**시킨다. `/health`가 상수를
반환하는 건 게으름이 아니라 liveness용으로는 **정확히 옳은 설계**다. 잘못은 *그것을 readiness로도
쓰던 것*이었다(원장 `L-15`).

그래서 기동 확인은 **둘 다** 본다:

```bash
POD=$(kubectl get pod -l app=core-api -o jsonpath='{.items[0].metadata.name}')
kubectl exec $POD -- sh -c 'wget -qO- http://localhost:8080/health; echo'
kubectl exec $POD -- sh -c 'wget -qO- http://localhost:8080/actuator/health/readiness; echo'
```
```
{"result":"SUCCESS","data":"DevQuest API is running","error":null}
{"status":"UP","components":{"db":{"status":"UP",...},"ping":{"status":"UP"}}}
```

> ⚠️ `mail`이 `readiness`에 **없어야 정상**이다. 학습 클러스터엔 SMTP 자격증명이 없어
> `/actuator/health`(전체)는 **503**을 준다 — 메일 발송 능력과 HTTP 요청 처리 능력은 무관한데
> 그것 때문에 트래픽이 끊기면 안 되므로, readiness 그룹을 `db,ping`으로 **명시적으로 좁혔다.**
>
> 🔑 **`/actuator/health/readiness`는 `SecurityConfig`에서 별도로 `permitAll`돼 있다.**
> `"/actuator/health"` 매처는 그 경로 **하나만** 매칭하므로, 하위 경로는 `/actuator/**`의
> IP 제한(`127.0.0.1`)에 걸린다. kubelet은 **파드 IP로** 찌르기 때문에 그대로 두면 **403 →
> 파드가 영영 Ready가 안 된다.** 나머지 actuator 엔드포인트의 IP 제한은 유지된다.

### 3b-8. ★ 이 단계의 목표 — 부수고 다시 짓기

먼저 **증거가 될 행**을 넣는다. 마이그레이션이 만드는 데이터로는 증명이 안 되기 때문이다
(뒤에서 설명).

```bash
kubectl exec postgres-0 -- psql -U devquest -d devquest -tAc "
CREATE TABLE IF NOT EXISTS stage3b_proof(id serial primary key, note text, written_at timestamptz default now());
INSERT INTO stage3b_proof(note) VALUES ('written before cluster destroy');
SELECT count(*) FROM stage3b_proof;"
kubectl get pod postgres-0 -o jsonpath='{.metadata.uid}{"\n"}'      # 나중에 대조
```

**클러스터를 통째로 destroy** (3b-9의 순서를 따를 것) 후 다시 apply하고 3b-5를 반복한다.
그리고 확인:

```bash
kubectl logs postgres-0 | grep -iE "Skipping initialization|database system was shut down"
kubectl exec postgres-0 -- psql -U devquest -d devquest -tAc "SELECT note, written_at FROM stage3b_proof;"
```
```
PostgreSQL Database directory appears to contain a database; Skipping initialization
database system was shut down at 2026-08-07 15:17:08 KST     ← 이전 클러스터의 종료 기록
written before cluster destroy|2026-08-07 15:16:40.84631+09  ← 원본 타임스탬프 그대로
```

🔴 **`initdb`가 안 돌았다는 것이 결정적 증거다.** 행 개수는 약하다 — 예를 들어 질문뱅크 26행은
**마이그레이션이 만드는 숫자**라(V10 5행 + V11 21행) 빈 볼륨에 새로 적용해도 똑같이 26이 나온다.
즉 *"살아남았다"* 와 *"똑같이 다시 만들어졌다"* 를 **구분하지 못한다.**

| 증거 | 증명하는 것 |
|---|---|
| 행 개수가 같다 | **동등성** — 내용이 같다 |
| `initdb` 건너뜀 + 이전 클러스터의 종료 시각 | **동일성** — 같은 것이다 |

Stage 3b가 묻는 것은 **동일성**이다.

### 3b-9. teardown — 3a와 순서가 다르다

```bash
kubectl delete externalsecret --all -A          # ① 소유자부터 (Secret 부활 방지)
kubectl delete secretstore --all -A
kubectl delete deployment core-api
kubectl delete statefulset postgres             # ② DB를 깨끗이 내린다
# 파드가 사라질 때까지 대기 → 볼륨이 detach된다
until [ -z "$(kubectl get pod postgres-0 --no-headers 2>/dev/null)" ]; do sleep 5; done
aws ec2 describe-volumes --region ap-northeast-2 --volume-ids <볼륨 ID> \
  --query 'Volumes[0].State' --output text      # → available 이 될 때까지
cd infra/aws-eks/2-cluster && tofu destroy      # ③ 인프라
```

🔴 **3a의 `kubectl delete pvc --all -A`를 그대로 하지 마라.** 3a에서는 그게 EBS를 회수하는
필수 단계였지만(`reclaimPolicy: Delete`), 3b에서는 `Retain`이라 볼륨이 남는다 — 대신 PV가
`Released` + `claimRef` 잔존 상태가 되어 다음에 **새 PVC를 자동으로 받지 않는다.**
어차피 클러스터를 destroy하면 K8s 객체는 전부 사라지므로, 3b에서는 PVC를 지울 이유가 없다.

> 재사용해야 하는 상황이 생기면 claimRef를 비운다:
> `kubectl patch pv postgres-data -p '{"spec":{"claimRef":null}}'`

**destroy 후 확인 — 볼륨은 살아 있어야 정상이다**:
```bash
aws eks list-clusters --region ap-northeast-2                 # → (없음)
aws ec2 describe-instances --region ap-northeast-2 \
  --filters "Name=instance-state-name,Values=running" --query 'Reservations[].Instances[].InstanceId' --output text   # → (없음)
aws ec2 describe-volumes --region ap-northeast-2 \
  --filters "Name=tag:Persistent,Values=true" --query 'Volumes[].[VolumeId,State]' --output text
```
```
vol-0518b6d0dcd2b0d70   available     ← 이게 남는 것이 이 단계의 성공 조건
```

### Stage 3b 결산 (실측, 2026-08-07)

과금 구간 **2회, 합 97분** (중간에 24분의 무과금 공백이 있다)

| 단계 | 실측 |
|---|---|
| apply (29개 리소스, RDS 포함) | **8분 27초** |
| destroy (30개) | 6분 35초 |
| 재구축 apply (30개) | 9분 40초 |
| 최종 destroy | 6분 21초 |
| **세션 합계** | **≈ $0.21** |

> 사전 추정은 $0.15~0.18이었다. 초과분의 정체는 인프라가 아니라 **디버깅 시간**이다
> (비밀번호 갈라짐 원인 파악 7분). 다음 추정에 반영할 것:
> *"검증 세션은 예상 실패 1건당 10분을 더한다."*

| | Stage 3a (동적) | Stage 3b (static) |
|---|---|---|
| 볼륨 소유자 | K8s(CSI) | **terraform (`0-bootstrap`)** |
| 수명 | 세션과 함께 소멸 | **영속 ($0.91/월)** |
| reclaimPolicy | `Delete` (고아 방지) | **`Retain`** (데이터 보호) |
| `volumeClaimTemplates` | 사용 | **사용 안 함** (PVC를 직접 작성) |
| AZ | `WaitForFirstConsumer`가 가려줌 | **노드를 볼륨 AZ로 고정해야 함** |
| 비밀번호 수명 | 아무래도 무관 | **볼륨과 같아야 함** (안 그러면 로그인 불가) |
| teardown 시 PVC 삭제 | **필수** (안 하면 볼륨이 샌다) | 불필요 |

> **결론**: 영속 볼륨은 "볼륨 하나 안 지우기"가 아니다. **볼륨의 수명이 다른 모든 것의 수명을
> 다시 계산하게 만든다** — 비밀번호, AZ, reclaim 정책, teardown 순서까지. 3a에서 옳았던 선택이
> 하나씩 뒤집힌다.

---

## Stage 4~5 — (예정)

| Stage | 세울 것 | 새로 배우는 것 |
|:--:|---|---|
| **4** | AWS Load Balancer Controller → ALB Ingress | IngressClass, ALB target-type |
| **5** | metrics-server·HPA, Karpenter, ArgoCD | 오토스케일, GitOps |
