# EKS 이관 작업 일지

> **용도**: "AWS free tier $200 EKS 실습기" 블로그 원고 소스. 다듬은 문장 말고 **사실 위주** —
> 명령어·출력·숫자를 그대로 남긴다. 요약은 블로그 쓸 때 한다.
>
> **기록 규칙** (상세: 루트 `CLAUDE.md` "EKS 작업 일지 규칙"):
> - 이벤트 발생 즉시 append (몰아쓰기 금지) — 비용 결정 / 아키텍처·도구 선택 / 막힘 / 해결 / 예상과 달랐던 것
> - 엔트리 태그: `[비용]` `[결정]` `[막힘]` `[해결]` `[메모]` 중 하나
> - 에러 메시지 원문 그대로 + 당시 상태(kubectl 출력 등) 포함
> - 비용 변동 시 아래 누적 테이블 갱신
>
> **주의**: "이관"은 학습 목적 배포를 말한다. **prod는 Fly.io 유지** — prod 이전은 검토 후 기각
> (근거: `infra/aws-eks/README.md`).

## 누적 비용

| 리소스 | 시작일 | 종료일 | 예상 비용 | 크레딧 잔여 |
|--------|--------|--------|----------|------------|
| (없음 — 착수 전) | — | — | $0 | $200.00 (실측, 07-16 콘솔 Credits — $100+$20×5, 만료 2027-07-15) |

> 크레딧 잔여는 **추정치**. AWS Cost Explorer 실측값 확인 시 `(실측)` 표기로 갱신.

---

## 2026-07-16

- `[결정]` 기존 kind 로컬 학습 트랙(Stage 1~3, `k8s/`) 폐기 — PR #269. AWS EKS 놀이터를 단독
  시작점으로 확정. 삭제 파일 8개(1,309줄), 복구 좌표: git 히스토리 #225 시점.
- `[결정]` DevSecOps 축 확장 검토 후 **기각**. 목표 고정: "Fly prod 유지 + DevQuest를 EKS에
  올리는 학습". 계획 문서 = `infra/aws-eks/README.md` (07-13 비용 분석: Fargate 상시 prod 월
  $35 = $200 크레딧 5.7개월 소진 → prod 이전 기각 / EKS 컨트롤플레인 $0.10/h → 3h 세션 $0.30 /
  NAT Gateway +$32/mo 금지 / 노드 t4g.small Spot).
- `[메모]` 작업 일지 체계 시작. 이 파일 + CLAUDE.md 규칙 추가 (세션 간 유지).
- `[메모]` 착수 전 남은 결정: AWS 계정 (기존+가드레일 vs 신규 $200 크레딧). 신규 계정은
  2025-07 이후 Free Plan 구조 — 크레딧 소진 시 리소스 중단 가능성, 가입 시 만료 동작 직접 확인 필요.
- `[비용]` **AWS 신규 계정 생성 완료** — $200/6개월 크레딧 대상 (기존 계정 재사용안 기각: 프리티어
  소진 상태였음). 크레딧 잔여 $200 (개설 시점, 소비 0). Free Plan 구조라 크레딧 소진 시
  리소스 중단 가능 — 만료 동작은 콘솔에서 추후 확인.
- `[메모]` Budgets 알림($10/$50/$150) + Cost Anomaly Detection **아직 미설정** — Stage 0
  `tofu apply` 전 필수 가드레일. `.claude/TASKS.md` TASK-4로 등록 (사용자 콘솔 작업).
- `[결정]` 기록 체계 확장 — 일지(실패 포함 사실 기록)와 별도로 **`docs/eks-tutorial-steps.md`
  정답 경로 문서** 신설 (성공 확인된 명령어만, 항상 처음부터 따라 하기 가능한 상태 유지).
  GUI는 스크린샷(`docs/images/eks-tutorial/`), 터미널은 텍스트. **완료 조건에 재현 검증 추가** —
  destroy 후 튜토리얼 문서만으로 재현 성공해야 종료.
- `[결정]` 스크린샷 규칙 교체 — 에이전트 브라우저 캡처 폐기, **사용자 직접 캡처**로 전환.
  에이전트는 `<!-- 캡처 필요: step-XX-... -->` 자리표시 + 튜토리얼 최상단 캡처 체크리스트 유지.
  휘발성 화면은 즉시 "지금 찍어야 함" 알림.
- `[메모]` **크레딧 구조 실측 — 예상과 다름.** $200 단일 크레딧이 아니라
  **$100 (AWS Free Tier 기본) + $20×5 (Explore AWS 활동: EC2 인스턴스 / Bedrock / Lambda /
  cost budget / RDS)** = 6건 분할. 전 건 Status Active, 사용 $0.00, 만료 **2027-07-15**
  (가입 +1년 — 계획 문서의 "6개월" 표기와 다름). "Set up a cost budget using AWS Budgets"가
  크레딧 지급 활동 목록에 있음 — 우리가 어차피 할 가드레일 설정이 $20 지급 조건.
- `[비용]` **예산 `eks-credit-guard` 생성.** Cost budget / Monthly Recurring / Fixed **$200** /
  All AWS services / Unblended costs. 알림 3건: **Absolute value 10 / 50 / 150** ($10/$50/$150),
  전부 **Actual** 트리거, 이메일 수신 등록. 예산 자체 비용 $0 (계정당 2개 무료).
- `[막힘]` 크레딧 charge type 제외 불가 — 신규 계정. Budget scope에서
  `Filter specific AWS cost dimensions → Charge type → Excludes` 값 드롭다운이 비어 있고 원문:
  `Data is not available. Please try to adjust the time period. If just enabled Cost Explorer,
  data might not be ingested yet`. Cost Explorer 데이터 미수집(최대 24h)이 원인.
- `[해결]` 우회: **All AWS services로 예산 먼저 생성**, 크레딧 제외 필터는 **~24h 뒤 편집으로 추가**
  (TASK-5 등록). 첫 실제 과금은 Stage 0 apply 때라 순서 여유. 미조치 리스크: 그 사이 과금 시
  크레딧이 청구액을 가려 알림이 늦을 수 있음(단 apply 전엔 과금 자체가 없음).
- `[막힘]` **알림 단위 함정.** Configure alerts Threshold 기본 단위가 `% of budgeted amount`.
  10/50/150 입력 시 예산 $200 기준 **$20/$100/$300**이 됨 (150%=$300은 크레딧 초과 → 무의미).
  → **Absolute value로 전환**해 $10/$50/$150로 교정. Alert#1 Summary `greater than $10.00 (5.00%)` 확인.
- `[메모]` **remote 세션 = 스크린샷 파일화 불가(확인됨).** 채팅 인라인 이미지/파일이 실행 디스크에
  안 떨어지고(Downloads·Temp·AppData 전수 검색 0건), 클립보드도 격리(`Get-Clipboard -Format Image`
  및 STA 자식 프로세스 모두 NULL — window-station 분리). → 이미지는 **base64 텍스트** 또는
  **local 세션/클론**에서만 확보 가능. 텍스트 절차가 본체이므로 이미지는 후속으로 미룸.
  튜토리얼 캡처 체크리스트에 미완(☐)으로 남겨 추적.
- `[해결]` **remote 이미지 전송 파이프라인 확립.** 사용자가 캡처를 **GitHub 댓글창에 Ctrl+V**로
  붙이면 자동 업로드되어 `github.com/user-attachments/assets/<id>` URL 생성 → 그 URL을 채팅에 주면
  에이전트가 다운로드. **주의: 익명 접근은 404**(`logged_in=no`) → `gh` 토큰 헤더 필요:
  `curl -sSL -H "Authorization: token $(gh auth token)" -o <경로> <url>`. TLS는 curl이 처리
  (PS5.1 `Invoke-WebRequest`는 `연결이 닫혔습니다`로 실패했음). 첫 성공: budget-amount/scope 2장 저장.
- `[결정]` **IaC-first 전면 채택 — "인프라 전부를 코드로, 콘솔 클릭 0".** 엔터프라이즈 실무 구조 차용:
  **레이어별 state 분리** `0-bootstrap`(remote backend·OIDC·IAM·예산·이상탐지, long-lived) /
  `1-network` / `2-cluster`(destroy 대상) / `gitops`(ArgoCD). 두 평면 분리(OpenTofu ↔ ArgoCD).
  CI: plan-on-PR + tfsec, apply-on-merge(GitHub OIDC, 장기키 없음). `infra/aws-eks/README.md` 갱신.
- `[결정]` **콘솔 예산은 삭제하지 않고 유지** — 현재 유일한 활성 가드레일. `0-bootstrap`에서
  `aws_budgets_budget` apply 후 **`terraform import`(권장·학습가치) 또는 삭제 후 재생성**으로 승격.
  콘솔 캡처 3장은 "수동 vs 코드" 대비 서사로 블로그에 활용.
- `[해결]` **TASK-5(크레딧 제외 필터 24h 대기) 불필요화** — IaC 예산은 `cost_types { include_credit=false,
  include_refund=false }`로 선언만 하면 됨(콘솔의 Cost Explorer 데이터 의존 없음). 즉시 적용 가능.
- `[메모]` **캡처 필요량 급감** — IaC-first면 단계가 코드+CLI 텍스트라 이미지 대부분 불필요.
  잔여 캡처는 계정 가입·Credits $200·"실제 됐다" 증빙 등 서사/증빙용 소수. remote 전송 병목 해소.
- `[결정]` **secret 감지 2층 분리 도입 예정** — 코드단 gitleaks(CI), 인프라단 tfsec/Checkov(CI).
  현재는 `assert-skill-safety.sh`(`.claude/skills/*.md`만) + LLM PR 리뷰뿐 — 레포 전역 결정론 스캐너 없음.
  ⚠️ public repo: **tfstate git 커밋 절대 금지**(`*.tfstate*` gitignore + S3 backend), account ID는 tfvars로.
- `[막힘]` 🔴 **치명적 함정 — IAM Identity Center를 Organizations로 켜면 $200 크레딧 즉시 소멸.**
  자격증명 방식으로 SSO(IAM Identity Center)를 시도 → "Enable IAM Identity Center with AWS Organizations"
  확인 화면에 경고 원문: *"Creating an organization automatically upgrades your account from a free plan
  to a paid plan with a pay-as-you-go pricing and **your free tier credits expire immediately**."*
  → **Enable 누르기 직전 발견, 취소.** 신규 계정(2025-07+ Free Plan)에서 org 생성 = 무료플랜 이탈 = 크레딧 몰수.
  ⚠️ Identity Center 랜딩의 기본 **Enable 버튼도 이 org 경로로 이어짐** — 누르지 말 것.
- `[해결]` **자격증명 = IAM 사용자 액세스키로 전환** (org 안 만듦 → 크레딧 리스크 0). `AdministratorAccess`
  IAM 사용자 `bootstrap-admin` + CLI 액세스키. **에이전트는 시크릿 키를 못 다룸(규칙)** → 사용자가
  PC 터미널에서 **직접 `aws configure`**(region `ap-northeast-2`). 부트스트랩 후 GitHub OIDC로 전환하고 키 폐기 예정.
- `[메모]` 도구 설치(remote): OpenTofu는 **무권한 포터블**(GitHub zip → `%LOCALAPPDATA%\OpenTofu`,
  user PATH 등록) 성공. AWS CLI는 winget(msiexec)로 설치. winget `--silent`가 비대화형에서 로그 0바이트로
  진행 상황 안 보임 — 프로세스(msiexec) 생존으로 진행 확인.

---

## 2026-07-18

- `[해결]` **0-bootstrap 1단계 apply 완료 — remote backend 저장소 생성.** `tofu apply -auto-approve`로
  로컬 state에서 5개 리소스 생성: S3 버킷 `devquest-eks-tfstate-seoul`(버전관리 Enabled + AES256 암호화
  + 퍼블릭 4중 차단) + DynamoDB 락 테이블 `devquest-eks-tflock`(`PAY_PER_REQUEST`). `Apply complete!
  Resources: 5 added`. 로컬 `terraform.tfstate` 생성(4950B). 자격증명은 07-16 `aws configure`한
  `bootstrap-admin` 액세스키, `aws sts get-caller-identity` 유효 확인 후 진행.
- `[비용]` **이번 apply 실질 $0.** S3 = tfstate 수 KB(프리티어 5GB 내), DynamoDB 온디맨드 = 유휴 $0
  (락은 apply당 수 요청). 돈 나가는 컴퓨트·NAT·EKS 없음. 누적 비용 테이블 변동 없음($200 유지).
- `[막힘]` **Claude Code auto 모드 분류기가 `tofu apply`를 하드 차단.** 클라우드 생성=비가역이라
  자동승인 모드에서 분류기가 거부: *"Blocked by classifier ... Let the user decide how to proceed."*
- `[해결]` **`permissions.ask`에 `Bash(tofu apply:*)`·`Bash(tofu destroy:*)` 등록**(`.claude/settings.local.json`).
  auto 모드는 유지하되 이 둘만 명시적 승인 프롬프트를 강제 → 분류기 하드거부 대신 사용자 승인으로 통과.
  또 `tofu apply`(대화형)는 Bash 도구가 비대화형이라 tofu 자체 프롬프트에서 `error asking for approval: EOF` →
  `-auto-approve`로 tofu 프롬프트를 스킵하고 Claude Code ask 게이트로 승인받는 구조가 정답.
- `[해결]` **닭-달걀 2단계 완료 — state를 S3로 이관.** `backend.tf` 추가(`backend "s3"`, key=
  `0-bootstrap/terraform.tfstate`, dynamodb_table=`devquest-eks-tflock`, encrypt=true) 후
  `tofu init -migrate-state -force-copy`. 결과: `Successfully configured the backend "s3"!`.
  검증 — `aws s3 ls s3://devquest-eks-tfstate-seoul/0-bootstrap/` → `terraform.tfstate 4950`,
  로컬 `terraform.tfstate`는 0바이트로 비고 `.backup` 잔존, `tofu plan` = **No changes**(드리프트 0).
  이제 이 스택이 자기 state를 자기가 만든 버킷에 둔다(자기참조 backend). 키에 레이어명 prefix를 줘
  1-network/2-cluster와 한 버킷 공유하되 충돌 없음. backend 블록은 var 불가 → 버킷·테이블명 리터럴.
  `-force-copy`는 비대화형 Bash에서 tofu의 "copy state? yes" 확인을 자동 통과(로컬 .backup 있어 안전).
- `[메모]` **다음: 예산(`aws_budgets_budget`, `cost_types{include_credit=false}`)·GitHub OIDC·IAM
  베이스라인 코드 작성 → apply.** 콘솔 예산은 이후 import 또는 재생성으로 코드판에 승격.
