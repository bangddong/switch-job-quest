# AWS EKS 실습 — 정답 경로

> **용도**: "AWS free tier $200 EKS 실습기" 튜토리얼 소스. 처음 하는 사람이 **이 문서만 보고
> 처음부터 따라 하면 되는 상태**를 항상 유지한다.
>
> **유지 규칙** (상세: 루트 `CLAUDE.md` "EKS 작업 일지 규칙"):
> - 실패 시도는 여기 없다 (그건 `eks-migration-log.md`) — **동작 확인된 명령어만** 순서대로
> - 각 명령어에 기대 출력/확인 방법, 단계별 사전 조건(도구 버전·AWS 권한·앞 단계 산출물) 명시
> - 💰 비용 발생 시작 지점 표시 + 각 단계 cleanup 명령어
> - 방식이 바뀌면 과거 단계도 최종 방식으로 소급 갱신
> - 캡처는 사용자가 직접 — 필요 지점에 `<!-- 캡처 필요: ... -->` 자리표시, 아래 체크리스트로 추적
> - 터미널 출력은 이미지 금지, 텍스트 코드블록
>
> **최종 검증**: 이관 완료 후 클러스터 destroy → 이 문서만으로 처음부터 재현 성공해야 완료.

## 캡처 체크리스트

> 위치: `docs/images/eks-tutorial/`. 캡처 넣으면 완료 체크 + 본문 자리표시를 이미지 참조로 교체.
> ⚠️ = 휘발성 (지금 아니면 못 찍음)

| 파일명 | 필요한 화면 | 완료 |
|--------|------------|:----:|
| step-00-credits.png | ⚠️ Billing → Credits: Total remaining **$200.00 / 사용 $0.00** + Active credits 6건 목록 ($100 Free Tier + $20×5 Explore AWS). 크레딧 소비 시작(Stage 0 apply) 후엔 재현 불가 | ☐ |
| step-00-budget-alerts.png | 예산 알림 설정 화면 — Threshold **Absolute value** 10/50/150, Trigger **Actual**, Email recipients (자리표시 `your_email@example.com`로 캡처 권장) | ☐ |
| step-00-budgets.png | Budgets 목록에 `eks-credit-guard` + 임계값 알림 표시 | ☐ |
| step-00-anomaly.png | Cost Anomaly Detection 모니터 생성 완료 화면 | ☐ |

> **이미지 확보 주의**: remote 세션에서는 채팅에 붙인 이미지·파일이 실행 디스크에 도달하지 않고
> 클립보드도 공유되지 않는다(확인됨). 이미지는 base64 텍스트로 전달하거나 local 세션/클론에서
> 저장·커밋한다. 텍스트 절차가 본체이므로 이미지는 후속으로 미뤄도 무방.

## 사전 조건 (전체 공통)

<!-- 확정 시 기입: OS/셸, OpenTofu 버전, AWS CLI 버전, kubectl 버전, IAM 권한 -->

- AWS 계정: 신규 계정 — 생성 완료 (2026-07-16)
- 크레딧 (07-16 콘솔 실측): **$200.00 = $100 (AWS Free Tier 기본) + $20×5 (Explore AWS 활동:
  EC2·Bedrock·Lambda·Budgets·RDS)**. 전 건 만료 **2027-07-15** (가입 +1년). 사용 $0.00
- AWS Budgets 알림 + Cost Anomaly Detection 설정 (Step 0 — `tofu apply` 전 필수)

## Step 0 — AWS 비용 가드레일 (예산 알림 + 이상 탐지)

> **사전 조건**: AWS 계정 로그인 (신규 학습 계정이면 루트로 진행해도 무방).
> **왜 먼저 하나**: EKS는 시간당 과금이라, `tofu apply` 전에 폭주 감지 장치부터 건다.
> **💰 비용**: 예산은 계정당 **2개까지 무료**(이후 $0.02/budget/day). 이상 탐지는 무료. → 이 단계 과금 $0.

<!-- 캡처 필요: step-00-credits.png — Billing → Credits 페이지, Total remaining $200.00·Active credits 6건이 보이는 상태 (⚠️ Stage 0 apply 전에 확보) -->

### 0-1. 예산(Budget) 생성

Billing and Cost Management 콘솔 → 좌측 **Budgets** → **Create budget**

1. **Choose budget type**
   - Budget setup: **Customize (advanced)**
   - Budget types: **Cost budget - Recommended** → Next
2. **Set your budget**
   - Details → Budget name: `eks-credit-guard`
   - Set budget amount → Period **Monthly** / Budget renewal type **Recurring budget** /
     Budgeting method **Fixed** / Enter your budgeted amount **`200`** (= 크레딧 총액)
   - Budget scope → **All AWS services (Recommended)** / Aggregate costs by **Unblended costs**
     - ⚠️ **크레딧 제외는 신규 계정에서 지금 불가** — 아래 "함정 ①" 참조. 24h 뒤 편집으로 추가.
   - Next
3. **Configure alerts** → **Add alert threshold** 3개 (전부 아래처럼):

   | Threshold 값 | 단위 | Trigger | Email recipients |
   |:---:|---|---|---|
   | `10` | **Absolute value** | Actual | `<your-email>` |
   | `50` | **Absolute value** | Actual | `<your-email>` |
   | `150` | **Absolute value** | Actual | `<your-email>` |

   - ⚠️ **단위 반드시 `Absolute value`** — 기본값 `% of budgeted amount`이면 150이 **$300**(크레딧 $200 초과 → 영영 안 울림). "함정 ②" 참조.
   - 확인: Alert #1 Summary가 `When your actual cost is greater than $10.00 (5.00%)...`로 뜨면 정상.
   <!-- 캡처 필요: step-00-budget-alerts.png — 위 알림 3개 중 하나. 이메일은 your_email@example.com 자리표시로 캡처 -->
4. **Attach actions** — Optional, 건너뛰기 → Next
5. **Review** → **Create budget**

<!-- 캡처 필요: step-00-budgets.png — Budgets 목록에 eks-credit-guard와 알림이 보이는 상태 -->

#### 함정 ① — 신규 계정은 크레딧 charge type을 아직 못 고른다
크레딧이 청구액을 $0으로 가려 알림이 안 울리는 것을 막으려면 원래
`Budget scope → Filter specific AWS cost dimensions → Dimension: Charge type → Excludes → Credit, Refund`로
크레딧/환불을 제외해야 한다. **그러나 계정 생성 직후엔** 이 Values 드롭다운이
`Data is not available. Please try to adjust the time period. If just enabled Cost Explorer,
data might not be ingested yet`를 띄우며 비어 있다 — Cost Explorer 데이터가 아직 수집 전(최대 24h).
→ **일단 All AWS services로 예산을 만들고, ~24h 뒤 예산을 편집해 이 필터를 추가한다.**
(첫 실제 과금은 Stage 0 `tofu apply` 때라 순서 여유 있음.)

#### 함정 ② — 알림 단위 기본값이 퍼센트다
Configure alerts의 Threshold 단위 기본값은 `% of budgeted amount`. 여기에 10/50/150을 넣으면
예산 $200 기준 **$20/$100/$300**이 된다 (150%=$300은 크레딧 초과라 무의미). 반드시 각 알림에서
단위를 **`Absolute value`**로 바꿔 10/50/150 = **$10/$50/$150 달러**가 되게 한다.

### 0-2. Cost Anomaly Detection (이상 탐지)

Billing 콘솔 좌측 **Cost Anomaly Detection** → **Create monitor**

<!-- 절차 확정 후 기입 — 진행 중 -->
<!-- 캡처 필요: step-00-anomaly.png — 모니터 생성 완료 화면 -->

### 0-3. Cleanup
- 예산·이상 탐지는 **삭제 불필요** (무료, 상시 유지가 목적). 학습 종료 후에도 남겨둔다.

## Step 1 — (예정) OpenTofu 설치 + 스캐폴딩

<!-- Stage 0 착수 시 기록 -->
