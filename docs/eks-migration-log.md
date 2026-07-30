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
| (현재 살아있는 것 없음) | — | — | $0/h | **≈ $199.6 / $200** |

**누적 실사용 (Cost Explorer 실측, 07-28 조회):**

| 기간 | 실사용량 | 비고 |
|---|---|---|
| 2026-01 ~ 06 | $0 | 계정 개설~착수 전 |
| 2026-07 (07-28 세션 전까지) | **$0.3283** | Task 8 · Stage 1 등 EKS 세션 3회 |
| 2026-07-28 Stage 2 세션 | ~$0.06 (추정) | 26분 35초 · CE 반영 ~24h 지연 |
| 2026-07-30 Stage 3a 세션 | ~$0.06 (추정) | 27분 · in-cluster Postgres 전환 |
| **누적** | **≈ $0.45** | 크레딧의 **0.23%** |

> 🔎 **조회 시 함정**: `RECORD_TYPE` 분리 없이 Cost Explorer를 보면 크레딧이 상계돼
> **순액 $0.00000004**만 나온다 — "아직 아무것도 안 썼네"로 오독하게 된다. 실사용량은 이렇게:
> ```
> aws ce get-cost-and-usage --time-period Start=YYYY-MM-01,End=YYYY-MM-DD \
>   --granularity MONTHLY --metrics UnblendedCost \
>   --filter '{"Dimensions":{"Key":"RECORD_TYPE","Values":["Usage","Credit"]}}' \
>   --group-by Type=DIMENSION,Key=RECORD_TYPE
> ```
> 크레딧 총액 $200 (07-16 콘솔 실측 — $100+$20×5, 만료 2027-01-15).

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
  cost budget / RDS)** = 6건 분할. 전 건 Status Active, 사용 $0.00, 만료 ~~**2027-07-15**
  (가입 +1년 — 계획 문서의 "6개월" 표기와 다름)~~ → **🔴 07-29 정정: `2027-01-15`가 맞다.**
  이 줄의 "가입 +1년"은 콘솔 값이 아니라 **추론이 값을 덮어쓴 오독**이었고, 그래서 원래 계획의
  "6개월" 표기가 맞았다. "Set up a cost budget using AWS Budgets"가
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
- `[해결]` **예산 코드화 apply 완료 — `aws_budgets_budget.monthly`.** `budget.tf` 신규:
  기준 $200/월, `cost_types{include_credit=false, include_refund=false}`,
  알림 3단계 `ABSOLUTE_VALUE` $10/$50/$150(GREATER_THAN, ACTUAL) → dynamic block로 리스트에서 생성.
  이메일은 `sensitive` 변수 → gitignore되는 `terraform.tfvars`에 값(`.example`은 커밋). `Apply: 1 added`.
  CLI 검증: `describe-notifications-for-budget` → 3건 절대값 $10/$50/$150 확인.
- `[결정]` **`ABSOLUTE_VALUE` + `include_credit=false` 조합이 핵심.** %기준이면 $200 기준 $20/$100/$300으로
  어긋나고(콘솔 함정 재현), 크레딧 포함이면 $200 소진 전까지 알림 침묵 → 학습장 무방비. 절대값 실요금 기준이
  크레딧 남아도 실제 $10 쓰면 발동하는 진짜 가드레일.
- `[비용]` 예산 리소스 $0 (계정당 2개까지 무료). 현재 코드판 `devquest-eks-monthly` + 콘솔판
  `eks-credit-guard` = 2개 공존, 아직 무료 구간. 콘솔판 삭제 시 1개.
- `[해결]` **콘솔 예산 `eks-credit-guard` 삭제 → 코드판 `devquest-eks-monthly` 단독.** 커밋 `eae1268`.

- `[결정]` **보안 점검을 "손"에서 "기계 강제"로 — CI 스캐너 2층을 OIDC보다 먼저 도입.**
  계기: 사용자가 "매 작업마다 보안 점검 잘 되나" 질문 → 감사 결과 **자동 방어는 .gitignore 하나뿐**,
  나머지는 수동+비일관(첫 apply에서 account ID 평문 노출한 전례). 결정론 스캐너 0개(gitleaks·tfsec·
  checkov·trivy 전무), CI 6개 워크플로 중 IaC/시크릿 스캔 0개 확인.
- `[해결]` **`.github/workflows/infra-ci.yml` 신설** — gitleaks(시크릿, `fetch-depth:0` 전체 히스토리)
  + tfsec(IaC, `working_directory: infra/aws-eks`) 2 job. 매 PR 자동 실행, 발견 시 머지 차단.
- `[해결]` **tfsec 로컬 선점 트리아지 — 5 findings.** Docker 데몬 미기동 → tfsec 바이너리 포터블
  다운(`v1.28.14`, `/tmp/tfsec.exe`)로 커밋 전 미리 검사. 결과 판단:
  - **고침(무료)**: `aws-dynamodb-enable-at-rest-encryption`(HIGH) → `server_side_encryption{enabled=true}`
    추가. apply 결과 `SSEType:KMS, ENABLED`(AWS 관리키, 키요금 $0). in-place 업데이트, 재생성 없음.
  - **근거 달고 무시(`#tfsec:ignore:`)**: `aws-s3-encryption-customer-key`(HIGH, AES256 유지 — CMK 월 ~$1
    과투자, 사용자 결정) / `aws-s3-enable-bucket-logging`(MED, 별도 로그버킷 과함) /
    `aws-dynamodb-enable-recovery`(MED, 락 테이블은 복구할 데이터 없음) / `aws-dynamodb-table-customer-key`(LOW).
  - `[막힘→해결]` `#tfsec:ignore`는 **finding이 붙는 리소스**에 정확히 달아야 함. s3 암호화 finding은
    `aws_s3_bucket`이 아니라 `aws_s3_bucket_server_side_encryption_configuration`에 붙음 → 주석 위치
    옮기니 해소. 최종 `No problems detected!`(9 passed, 4 ignored, 0 problem).
- `[해결]` **gitleaks 히스토리 선점 스캔 — 2건 잡힘, 검증 결과 오탐 확정.** 바이너리 포터블
  다운(`v8.30.1`, windows는 `.zip`) → `gitleaks git --redact` 499커밋 스캔. 2건 모두
  `monitoring/config.alloy:2`(generic-api-key, entropy 3.72). `git show`로 확인: 해당 라인은 **주석**이고
  실제 Grafana 키는 line 21 `env("GRAFANA_API_KEY")` 참조(하드코딩 아님), 파일은 #172에서 삭제됨.
  → `.gitleaksignore`에 지문 2개 + 근거 기록 후 재스캔 `no leaks found`. **히스토리에 실제 유출 없음.**
- `[메모]` 잔여 저위험: `monitoring/config.alloy` 히스토리에 Grafana 인스턴스 ID(username) 평문 존재 —
  credential 아닌 식별자라 gitleaks 미검출. 히스토리 재작성(filter-repo)은 과투자로 보류.
- `[해결]` **draft PR #283 개설 — 보안 CI 실증.** feat/ 브랜치라 `assert-qa-run.sh` 훅이 PR 생성 차단
  → qa-reviewer 실행(HIGH 0, MEDIUM 1=paths filter 효율성, LOW 2)해 마커 생성 후 개설.
- `[막힘]` 🔴 **CI gitleaks FAIL인데 로컬은 통과 — 거짓 그린.** CI가 `.gitleaksignore:4`를 generic-api-key로
  검출. 원인: **오탐을 문서화하며 유발 문자열(scrape 대상 경로)을 주석에 그대로 인용** → 자기검출(자책골).
  로컬이 통과한 건 `gitleaks git`이 **커밋 히스토리만** 스캔하는데 그 시점 `.gitleaksignore`가 미커밋
  워킹트리라 파일 자체가 스캔 대상이 아니었기 때문. 즉 **로컬 검증 방식이 CI와 불일치**했다.
- `[해결]` 3단계로 수습: ① 주석에서 문자열 제거(16117ec) ② 그래도 과거 커밋 blob(64a0bd8)에 남아
  검출 → 그 지문 `64a0bd8:.gitleaksignore:generic-api-key:4` 등록(cc99728) ③ **커밋 후** `gitleaks git`
  502커밋 `no leaks found` 재확인 → push → CI **gitleaks PASS + tfsec PASS**.
- `[메모]` **교훈**: git-모드 시크릿 스캐너는 **커밋 후** 검증해야 CI와 일치한다(워킹트리 파일은 안 봄).
  그리고 오탐을 문서화할 땐 유발 문자열을 그대로 인용하지 말 것. **기계 강제(CI)가 사람 손(로컬)이
  놓친 걸 잡은 실제 사례** — "매 작업 보안 점검"을 손이 아니라 CI로 옮긴 결정의 정당성.
- `[메모]` application-local.yml의 실제 flyio-access-token(엔트로피 5.87)은 **gitignore 확인** — 커밋 안 됨,
  CI 스캔 대상 아님. (gitleaks dir 모드가 디스크 전체를 훑어 48건 노이즈를 냈으나 전부 gitignore된 로컬 파일)
- `[해결]` **GitHub OIDC + IAM 역할 apply 완료 — 장기키 없는 CI 인증 확립.** `iam-github-oidc.tf` 신규,
  3 리소스: `aws_iam_openid_connect_provider.github`(발급자 token.actions.githubusercontent.com,
  aud=sts.amazonaws.com, 지문은 `tls_certificate` data로 동적 조회 → `tls` provider v4.3.0 추가) +
  `aws_iam_role.github_actions` + `AdministratorAccess` 첨부. 출력 `github_actions_role_arn` 추가.
- `[결정]` **역할 권한 = AdministratorAccess + 신뢰정책 강잠금** (사용자 선택). 근거: 학습 전용계정
  (prod는 Fly 별도)이라 폭발 반경이 이 계정에 갇히고, EKS apply가 권한부족으로 막힐 일 없음. 보안 경계는
  "무엇을 할 수 있나"가 아니라 **"누가 assume 가능한가"**로 이동 — 신뢰정책 `sub`를
  `repo:bangddong/switch-job-quest:ref:refs/heads/main` + `:pull_request`로 한정. prod였다면 최소권한 스코프.
- `[해결]` CLI 검증: `get-role`의 AssumeRolePolicyDocument에서 sub 2건(main/PR)·aud(sts) 정확,
  `AdministratorAccess` 첨부, OIDC provider 등록 확인. `tfsec` No problems(admin 첨부는
  `#tfsec:ignore:aws-iam-no-policy-wildcards` 근거 주석). `tofu plan` No changes.
- `[비용]` IAM·OIDC 리소스 $0.
- `[해결]` **apply-plan CI 파이프라인 신설 — `.github/workflows/infra-deploy.yml`.** plan-on-PR(step
  summary 출력) + apply-on-merge(main). OIDC로 `aws-actions/configure-aws-credentials@v4`가 역할 assume,
  `opentofu/setup-opentofu`로 tofu 설치. `concurrency.cancel-in-progress=false`(apply 중 취소 방지),
  `permissions.id-token: write`(OIDC 필수).
- `[결정]` **CI 관리 대상은 우선 0-bootstrap** (matrix `layer: [0-bootstrap]`) — 존재하는 유일 레이어라
  OIDC end-to-end 검증 가능. 상위 레이어는 matrix에 추가만. self-관리 리스크는 PR리뷰+스캐너로 완화,
  최악 시 로컬 admin 키(bootstrap-admin)로 복구.
- `[결정]` **역할 ARN·예산 이메일은 GitHub Secret으로** (커밋 파일에 account ID·이메일 못 넣음, public repo).
  `gh secret set AWS_ROLE_ARN`(tofu output에서), `BUDGET_EMAIL`. 워크플로는 `${{ secrets.* }}`로 주입.
- `[메모]` **다음: PR #283에서 infra-deploy plan-on-PR이 실제로 도는지 확인 → 0-bootstrap 완성 →
  PR ready·머지.** 이후 1-network(VPC) 착수.
- `[해결]` **0-bootstrap 종료 (#283 머지, #284 CONTEXT 정리, #282 orchestrator opus).** apply-on-merge가
  main에서 no-op(`0 added`) 확인 — 양방향 OIDC CI/CD 루프 완성. 열린 PR 0, 비용 $0.

- `[결정]` **1-network 착수 — 퍼블릭 서브넷 전용(NAT 회피).** README line 183 방침 그대로: NAT Gateway
  $32/mo 폭탄 회피 위해 노드를 퍼블릭 서브넷에 두고 `map_public_ip_on_launch=true`로 공인IP → IGW 경유
  인터넷(무료). prod면 private+NAT/endpoint지만 학습장 트레이드오프. 구성: VPC 10.0.0.0/16(DNS
  hostnames/support ON=EKS 필수) + IGW + 퍼블릭 서브넷 ×2(ap-northeast-2a/2c, /20) + 라우트(0.0.0.0/0→IGW).
  서브넷에 EKS discovery 태그(`kubernetes.io/role/elb=1`, `kubernetes.io/cluster/devquest-eks=shared`).
- `[해결]` tfsec: 공인IP(`aws-ec2-no-public-ip-subnet`)·flow logs(`require-vpc-flow-logs`) 2건은
  의도적 → `#tfsec:ignore` 근거 주석. `No problems detected`. `tofu plan` = 7 to add. 비용 $0.
- `[결정]` **apply는 CI 도그푸딩** — `infra-deploy.yml` matrix에 `1-network` 추가(`fail-fast:false`).
  PR→CI plan / merge→CI apply로 VPC 생성. 방금 만든 파이프라인을 실제 사용.
- `[메모]` state는 `1-network/terraform.tfstate` 키(0-bootstrap과 한 버킷). 이 레이어는 secret/tfvars 없음.

## 2026-07-19

- `[비용]` **크레딧 잔여 $200 재확인** (사용자 콘솔 Credits 조회) — 0/1 레이어 전부 $0라 소비 없음.
  2-cluster 착수 직전 시작점. 만료 ~~2027-07-15~~ → **07-29 정정: `2027-01-15`**.
- `[결정]` **2-cluster는 로컬 apply/destroy** (CI 도그푸딩한 0/1과 다름). 근거: 컨트롤플레인 $0.10/hr
  = 유휴 과금 자원이라 세션마다 올렸다 부수는 왕복이 잦음 → `tofu destroy` 한 줄이 CI 머지 왕복보다
  빠르고 비용 통제가 손에 잡힘. CI 관리 레이어는 상주형 0/1만 유지(infra-deploy matrix 그대로).
- `[결정]` **2-cluster 착수 — 브랜치 `chore/eks-2-cluster`.** 올릴 자원 예상 비용(서울 근사치):
  EKS 컨트롤플레인 $0.10/hr(유휴에도) / 노드 t4g.small Spot ×1 ~$0.006~0.008/hr / EBS gp3 노드 루트볼륨 /
  ECR·애드온·IAM·IRSA·OIDC는 $0. apply 전 plan 리소스 해설+사용자 확인 게이트 유지.

## 2026-07-20

- `[막힘]` 새 클론 머신에 IaC 툴체인 없음 — `tofu`/`terraform`/`tfsec` 전부 `not found`
  (07-19 clone된 환경, 어제 작업은 다른 머신). `brew install opentofu tfsec` → OpenTofu v1.12.4,
  tfsec v1.28.14 설치 후 진행. aws 자격증명은 `bootstrap-admin` IAM user 정상.
- `[결정]` **K8s 버전 계획 default `1.32` → `1.36`으로 상향.** apply 직전 재확인 방침대로 실측:
  ```
  aws eks describe-cluster-versions --region ap-northeast-2 \
    --query 'clusterVersions[?status==`STANDARD_SUPPORT`].[clusterVersion,defaultVersion,endOfStandardSupportDate]'
  1.36  True   2027-08-02   (default)
  1.35  False  2027-03-27
  1.34  False  2026-12-02
  1.33  False  2026-07-29   ← 9일 뒤 표준지원 종료
  ```
  `1.32`는 이미 표준지원 밖(목록에 없음). `1.33`은 만료 임박 → 회피. **default·최신·지원 최장인
  `1.36` 선택.** 애드온 3종은 버전 미명시 → 클러스터 버전(1.36) 기본값 자동 선택(apply 시 확정).
  ⚠️ 함정: `describe-cluster-versions` 필터 필드는 `clusterVersionStatus`가 아니라 `status`.
  잘못 쓰면 빈 출력(에러 없이) → 없는 문제로 오인. 원문 먼저 확인할 것.
- `[해결]` Task 1~7 완료(코드+plan, $0). 11개 `.tf` 각 태스크마다 `fmt`→`validate`→`tfsec` 통과
  (tfsec: 근거 ignore 8건 외 No problems). 실제 backend init 후 `tofu plan` = **`14 to add,
  0 to change, 0 to destroy`** (계획 예상치 일치, remote_state로 1-network VPC 정상 참조). apply는
  별도 세션(사용자 승인 게이트). QA 리뷰 HIGH 0 — 머지 가능.
- `[메모]` 아키텍처 다이어그램 상시 유지 시작 — mermaid 소스 `docs/architecture/eks-2-cluster.md`
  (repo, PR·블로그용) + 라이브 아티팩트(줌·전체화면). 이후 레이어/Stage마다 갱신.

## 2026-07-24 — Task 8: 2-cluster apply 왕복 (★첫 과금)

- `[메모]` 사전 점검(비용 $0): tofu v1.12.4·aws-cli 2.36.2·자격증명 `bootstrap-admin`(계정 <ACCOUNT>)
  전부 정상. **kubectl 미설치 발견 → `brew install kubectl` (v1.36.3) 설치** — 클러스터 1.36과 클라이언트
  버전 일치. K8s 버전 재확인: `1.36` 여전히 표준지원 최신(릴리스 26-06-02, 표준지원 종료 27-08-02).
  `1.33`은 5일 뒤(07-29) 종료 → 1.36 핀이 정확했음.
- `[비용]` `tofu init`(S3 backend 재연결)·`tofu plan` = **`14 to add, 0 to change, 0 to destroy`**.
  과금 리소스 2개뿐: 컨트롤플레인($0.10/hr) + 노드그룹 t4g.small×1 **ON_DEMAND**(#314로 SPOT→ON_DEMAND
  변경, 신규계정 Spot vCPU 쿼터=0이라 SPOT이면 apply 필패였음). 합산 ~$0.13/hr, 왕복 40분 ≈ $0.09.
  나머지 12개(애드온3·OIDC·IAM역할2·정책4·access2) $0.
- `[해결]` **apply 성공 (13:05 시작).** 컨트롤플레인 `Creation complete after 7m54s`, 노드그룹 1m27s,
  애드온(vpc-cni 14s·kube-proxy 24s·coredns 14s). 총 ~10분. `Apply complete! 14 added`.
  검증: `kubectl get nodes` → `ip-10-0-8-101` **Ready** / v1.36.2-eks / **arm64**(Graviton 확인) /
  EXTERNAL-IP 3.36.118.171(퍼블릭 IP = NAT 회피 설계 확인). kube-system: aws-node 2/2·coredns×2·
  kube-proxy 전부 Running. cluster_endpoint OIDC id=565A7F97... 발급됨.
- `[해결]` **destroy 성공.** 노드그룹 삭제 2m16s → 컨트롤플레인 1m23s → IAM 역할·정책·OIDC 순.
  `Destroy complete! 14 destroyed`. **teardown 전수 검증**: `tofu state list` 비어있음 · EC2 `terminated` ·
  `eks list-clusters` 비어있음 · **고아 리소스 0**(미연결 EBS·EKS 태그 SG·ELB/ALB·NAT 전부 없음).
  NAT 회피 설계 덕에 destroy 후 잔존 비용 $0.
- `[비용]` **첫 과금 왕복 결산**: 벽시계 apply-start(13:05)~teardown-verified(13:55) ≈ **50분**
  (순수 tofu compute는 apply ~10분 + destroy 실행 ~5분, 나머지는 refresh·대기). 컨트롤플레인
  ACTIVE ~40분 × $0.10/hr ≈ **$0.07** + 노드 t4g.small ON_DEMAND ~$0.01 + EBS 무시 = **총 ~$0.1 이하**.
  크레딧 $199.81 대비 무시할 수준. **교훈 확정: 아낄 것은 크레딧이 아니라 "켜놓고 딴짓하는 시간".**
- `[결정]` Task 8 완료 = **2-cluster IaC가 apply→검증→destroy 왕복으로 실증됨.** 코드가 실제로
  동작함을 확인. 다음은 Stage 1(ECR + 앱 배포) — 그 전 ECR을 0-bootstrap에 편입 필요(현재 `.tf` 0건).

## 2026-07-27 — Stage 1: ECR + 첫 앱 배포

- `[메모]` 사전점검 통과(도구·자격증명·K8s 1.36 표준지원 유효·과금 리소스 0·리퍼 로드됨).
  **준비물(무과금)**: ECR push 워크플로(OIDC→arm64 러너 네이티브 빌드→sha 태깅) + Dockerfile
  `SERVICE` build-arg 파라미터화 + k8s Deployment/Service(ClusterIP) 매니페스트.
- `[막힘]` **Dockerfile jar 글롭 함정 사전 발견**: `cp .../libs/*.jar`는 ai-api에서 깨진다.
  ai-api는 parity 테스트 요구로 plain jar도 켜져 있어 산출물이 2개(`ai-api-x.jar` + `ai-api-x-plain.jar`).
  → `-plain` 제외 + 개수 검증 후 복사로 수정. core-api는 plain 비활성이라 1개(실측 확인).
- `[결정]` **DB 없이 배포(B안) 확정.** 이유: ①`application-local.yml`엔 datasource 없음(로컬은 H2)
  ②Neon 크리덴셜은 Fly secrets에 있으나 **write-only라 되읽기 불가**(digest만 조회됨)
  ③무엇보다 **학습 클러스터를 prod DB에 연결하는 것 자체가 금지**(Flyway 실행·커넥션 경합·비가역 위험).
  → Stage 1 목표(ECR 이미지가 노드로 pull되어 파드가 뜨는가)는 DB 없이 100% 검증 가능.
  DB 연결은 Stage 2에서 IRSA/External Secrets로 정식 처리. CrashLoopBackOff 진단도 교보재로 활용.
- `[막힘]` **workflow_dispatch가 HTTP 404.** 원인: GitHub은 dispatch 워크플로 "정의"를 **기본 브랜치에서만**
  찾는다 — `--ref stage/eks-1-ecr`를 줘도 main에 파일이 없으면 404. → push 트리거 추가로 우회 시도.
- `[막힘]` **push 트리거로 돌리자 OIDC 거부**:
  ```
  X Could not assume role with OIDC: Not authorized to perform sts:AssumeRoleWithWebIdentity
  ```
  원인: 역할 신뢰정책의 sub 조건이 `refs/heads/main`·`pull_request` 두 개뿐(iam-github-oidc.tf).
  브랜치 push의 sub은 `repo:...:ref:refs/heads/stage/eks-1-ecr`라 매칭 실패. **보안이 의도대로 작동한 것.**
- `[해결]` 신뢰정책을 푸는 대신(역할이 AdministratorAccess라 위험) **PR 컨텍스트에서 빌드**.
  워크플로+Dockerfile만 `chore/ecr-push-workflow` 브랜치로 분리해 PR #323 생성 → `pull_request`
  이벤트 sub이 허용 목록과 일치 → OIDC 통과, 빌드·푸시 성공.
  (정석은 ECR 전용 최소권한 역할을 분리해 브랜치를 넓히는 것 — Stage 2 주제로 기록.)
- `[해결]` **ECR 푸시 성공**: `devquest/core-api:34a7a0ba...` + `latest`, ~166MB.
  **아키텍처 실측 `arm64/linux`** — Graviton 노드와 일치(x86이면 `exec format error`로 CrashLoop).
- `[메모]` 퀴즈 게이트 실동작 확인: `stage/eks-1-ecr`에서 `gh pr create` 시도 → 훅이 차단
  (`docs/eks-quizzes/stage-eks-1-ecr.md` 없음). 설계대로 작동.
- `[해결]` **apply 성공** (08:58 시작, 14 added). 노드 Ready(v1.36.2·arm64·EXTERNAL-IP 43.201.76.172).
- `[해결]` **★ Stage 1 핵심 검증 통과 — ECR→노드 이미지 pull 성공**:
  ```
  Normal Pulled kubelet Successfully pulled image ".../devquest/core-api:34a7a0ba..." in 3.193s
  Image size: 174153756 bytes
  ```
  **imagePullSecret 없이 성공** — 노드 IAM 역할의 AmazonEC2ContainerRegistryReadOnly가 작동(nodes.tf).
  같은 리전이라 166MB가 3.2초. 아키텍처 일치(arm64)라 `exec format error` 없음.
- `[막힘]` **CrashLoopBackOff — 원인이 DB가 아니었다.** 부팅 순서대로 숨은 환경 의존이 3단계로 드러남:
  ① **Loki 로깅**: `IllegalArgumentException: URI with undefined scheme` at `Loki4jAppender.start`.
     `logback-spring.xml`의 prod 프로파일이 LOKI appender를 **무조건** 참조하고
     `${grafanaLokiUrl}`이 비면 **로깅 시스템 초기화 단계에서 앱이 죽는다**(DB에 도달조차 못 함).
  ② **JWT_SECRET**: `Could not resolve placeholder 'JWT_SECRET'` → jwtProvider 빈 생성 실패.
  ③ **DB**: `HikariPool-1 - Starting...` → `BeanCreationException: entityManagerFactory` (의도된 종착점).
  → 교훈: **"Fly에서 잘 돌던 앱"은 Fly secrets가 가려주던 환경 의존을 갖고 있다.** 플랫폼을 옮기면
    그게 부팅 순서대로 드러난다. 사전에 `grep -ohE '\$\{[A-Z_]+...\}' application*.yml`로 전수 파악하는
    편이 두더지잡기보다 빠르다(실제로 그렇게 전환해 7개 필수 변수를 한 번에 확보).
- `[메모]` prod 필수 환경변수(기본값 없음) 7개: DB_HOST/NAME/USERNAME/PASSWORD · JWT_SECRET ·
  GITHUB_CLIENT_ID/SECRET. 그 외는 기본값 보유. Loki 3종은 logback에서 별도 요구.
- `[해결]` **teardown 클린** (destroy 09:18, 14 destroyed). 고아 전수검증 0(state·클러스터·EC2·LB·EBS·NAT).
  **ECR 이미지는 생존**(`34a7a0ba...`+latest) — 0-bootstrap 편입(#322) 결정이 작동: 클러스터를 부숴도
  이미지가 남아 다음 세션에 재빌드 불필요.
- `[비용]` **Stage 1 결산**: apply 08:58 ~ destroy 09:18 ≈ **20분, ~$0.05**. 크레딧 무손실.
  ECR 저장(빈 레포→이미지 1개 166MB) ≈ $0.02/월. 실습 후 남는 유일한 비용.

## 2026-07-28 — Stage 2 준비: RDS 재검토 + ESO 설계 (무과금 세션)

- `[메모]` **무과금 세션**. apply 없음. 사전 점검: tofu v1.12.4 · kubectl v1.36.3 ·
  aws-cli 2.36.2 정상. **helm 미설치 발견 → `brew install helm` (v4.2.3)** — ESO 설치에 필요.
  (Task 8의 kubectl 미설치와 같은 패턴: 도구 공백은 실제 세션 직전이 아니라 준비 단계에서 드러난다.)
  과금 리소스 전수 0 확인: EKS 클러스터·RDS 인스턴스·수동 스냅샷·실행중 EC2·LB 전부 없음.
- `[결정]` **RDS를 destroy-after-use로 Stage 2에 편입.** 07-22 "DB 전략 환경별 분리"(#313)에서
  RDS를 기각했던 3개 사유를 재판정한 결과:
  | 사유 | 재판정 |
  |---|---|
  | ① 클러스터를 꺼도 상시 과금 → 15%만 쓰고 100% 지불 | 🔴 **무효.** 클러스터와 함께 destroy하면 성립하지 않는다. 원래 기각안이 "RDS 상시 가동"만 상정했던 것 |
  | ② 클러스터 밖 관리형 Postgres는 Neon이 이미 그거고 공짜 | 🟡 **절반만 유효.** prod DB 대체 목적엔 맞으나 **학습 목적엔 틀렸다** — Neon은 AWS를 안 가르친다 |
  | ③ RDS는 EBS·PVC를 안 건드려 Stage 3 목표와 불일치 | 🔴 **여전히 유효** |
  → 결론: **RDS는 in-cluster Postgres의 대체가 아니라 Stage 2를 완성시키는 조각.**
  Stage 3에서 in-cluster StatefulSet+EBS로 스왑해 "관리형 ↔ 자체운영" 비교 실습으로 잇는다.
- `[비용]` **AWS Pricing API 실측** (ap-northeast-2, 일반론 아님):
  ```
  db.t4g.micro PostgreSQL Single-AZ : $0.0250/hr
  db.t4g.small PostgreSQL Single-AZ : $0.0510/hr
  db.t3.micro  PostgreSQL Single-AZ : $0.0280/hr
  RDS gp3 스토리지                   : $0.131/GB-Mo   (참고: EBS gp3 $0.0912/GB-Mo)
  t4g.small ON_DEMAND (EC2)          : $0.0208/hr
  ```
  3시간 세션 환산: 컨트롤플레인 $0.300 + 노드 $0.062 = **$0.36**,
  여기에 RDS db.t4g.micro $0.075 + gp3 20GB 시간환산 $0.011 = **$0.45**.
  **증분 $0.086/세션. 30세션 돌려도 $2.6 = 크레딧의 1.3%.** → **비용은 결정 요인이 아니다.**
- `[비용]` 🔴 **이 계정엔 RDS 프리티어가 없다** (실측: `aws freetier get-free-tier-usage` 9건 —
  Glue·KMS·Lambda·SNS·SQS·CloudWatch뿐, RDS 항목 없음). 2025-07 이후 신규계정은 12개월
  750시간 무료가 아니라 크레딧 구조라서. 위 단가가 그대로 청구된다는 뜻. (그래도 무의미한 금액)
- `[결정]` 시크릿 주입 도구 = **External Secrets Operator**. 기각안: ①Secrets Store CSI Driver
  (파드 재시작 없이 회전 반영은 강점이나 앱이 파일에서 읽어야 해 Spring 설정 변경 수반)
  ②IRSA + SDK 직접 호출(원리는 가장 선명하나 BE 코드 변경 필요 + Fly 배포 경로와 갈라져
  "코드 변경 0" 원칙이 깨짐). ESO는 기존 `k8s/base/core-api.yaml`의 `envFrom.secretRef`를
  그대로 두고 K8s Secret만 채워주므로 **앱·매니페스트 변경 0**.
- `[결정]` RDS 마스터 크리덴셜은 `manage_master_user_password = true` — AWS가 Secrets Manager에
  직접 생성/로테이션한다. 이러면 이번 Stage의 파이프라인이 한 줄로 이어진다:
  `RDS → (AWS 자동 생성) Secrets Manager → (IRSA 인증) ESO → K8s Secret → 앱 envFrom`.
  손으로 넣은 값을 다시 꺼내는 인위적 구성이 아니라 **AWS가 소유한 진짜 시크릿**이 대상이 된다.
- `[메모]` 착수 전 식별한 **새 리스크 3종** (설계에 미리 반영할 것):
  ① **새 고아 클래스** — `tofu destroy` 후에도 final snapshot·automated backup이 남아 과금된다.
     현재 SOP §9 고아 검증 목록에 RDS 스냅샷 항목이 **없다**. → `skip_final_snapshot`,
     `delete_automated_backups`, `backup_retention_period=0` + SOP 체크리스트 보강 필요.
  ② **tfsec 게이트 충돌 가능성** — 1-network는 퍼블릭 서브넷 2개(2a/2c)뿐이고 프라이빗이 없다.
     퍼블릭에 RDS를 두면 tfsec이 잡을 공산이 크다. → 프라이빗 서브넷 신설이 정답
     (RDS는 아웃바운드 인터넷이 불필요하므로 **NAT 없이 가능 = $0**). 서브넷 티어링도 학습 대상.
  ③ **리퍼 사각지대** 🔴 — dead man's switch(#320)는 `2-cluster`를 destroy한다. RDS를 다른
     레이어에 두면 **리퍼가 RDS를 안 죽여 방치 시 계속 과금**된다. 반드시 리퍼 사정권에 넣을 것.
- `[메모]` 🟡 helm이 **v4** 메이저다. ESO 차트가 Helm 4에서 렌더되는지 미확인 —
  `helm template`은 클러스터 없이 로컬에서 도므로 **무과금으로 선검증 가능**.
- `[막힘]` **Blindspot Pass(착수 전 불일치 진단)에서 계획을 바꿔야 할 결함 다수 발견.** 요약:
  - 🔴 **dead man's switch가 RDS 때문에 벽돌이 된다.** `aws_db_instance.skip_final_snapshot` 기본값이
    `false`라 그대로 두면 destroy가 **에러로 실패**한다. 리퍼는 `tofu destroy -auto-approve`를 통째로
    돌리므로(`.claude/scripts/eks-reaper.sh:75`) RDS 하나 때문에 **EKS 컨트롤플레인·노드그룹까지 살아남고**
    마커는 유지된 채 30분마다 같은 실패를 반복한다. 과금 안전장치가 RDS 때문에 죽는 구조.
  - 🔴 **그 실패를 감지할 수단이 없다.** 리퍼·하트비트의 생존 판정이 `aws eks list-clusters` 하나뿐
    (`eks-reaper.sh:48-54`, `eks-heartbeat-reminder.sh:23-31`). destroy가 EKS만 지우고 RDS에서 실패하면
    다음 주기에 "이미 정리됨"으로 오판 → **마커 자가삭제 → 감시 종료 → RDS 영구 과금.**
  - 🔴 **RDS는 반드시 `2-cluster` 레이어 안.** 리퍼가 대상 디렉토리를 하드코딩
    (`eks-session-marker.sh:22` → `eks-reaper.sh:57,73,75`). `3-data` 등으로 분리하면 리퍼 사각지대.
    추가로 `infra-ci.yml:55`의 `guard-local-layers`도 `2-cluster` 문자열만 막으므로, 새 레이어를
    `infra-deploy.yml` 매트릭스에 넣으면 **main 머지 순간 CI가 RDS를 자동 apply**해 버린다.
  - 🔴 **SOP §9 고아 검증에 RDS가 통째로 없다.** 인스턴스·수동 스냅샷(**final snapshot은 destroy 후에도
    남아 과금**)·서브넷그룹·Secrets Manager(삭제 대기 중도 $0.40/mo) 전부 미검증.
- `[막힘]` **"prod 필수 환경변수 7개"는 틀렸다 — 실제 10개.** logback이 `GRAFANA_LOKI_URL`·
  `GRAFANA_LOKI_INSTANCE_ID`·`GRAFANA_API_KEY`를 추가로 요구한다. yml은 `${VAR}`이고 logback은
  `source="VAR"` 형식이라 **Stage 1에서 자랑했던 `${...}` grep 팁이 정확히 이 3개를 놓쳤다.**
  (`k8s/README.md` 하단에 이미 실측 기록돼 있었는데 계획에 반영 안 됨 — 문서를 안 읽은 대가.)
- `[막힘]` **`manage_master_user_password=true`가 만드는 시크릿엔 `username`/`password`뿐.**
  `DB_HOST`·`DB_NAME`이 없어 ExternalSecret 하나로 4키를 못 만든다 → 별도 시크릿 또는 ESO `template` 합성 필요.
- `[결정]` **RDS를 프라이빗 서브넷이 아니라 기존 퍼블릭 서브넷 + `publicly_accessible=false` + SG 제한으로.**
  프라이빗 신설이 정석처럼 보였지만 실제로는 악수다: ①`1-network`는 **CI 자동 apply 대상**
  (`infra-deploy.yml:36`)이라 머지 즉시 적용되고 **리퍼 사정권 밖** ②`2-cluster`의
  `data.terraform_remote_state.network`는 **이미 apply된 state**를 읽으므로, 출력만 추가하고 apply 안 하면
  `tofu plan`이 `This object does not have an attribute named ...`로 실패(순서 의존성).
  RDS 서브넷그룹은 2 AZ만 요구하고 이미 2a/2c가 있다. 인터넷 도달 불가는 동일하게 성립.
- `[결정]` **logback을 조건부로 수정한다** (환경변수 주입 회피가 아니라 결함 수정).
  `logback-spring.xml:16-62`는 prod 프로파일이면 LOKI appender를 **무조건** 정의하고 `:60`에서 root에
  **무조건** 붙인다. `:18`의 `defaultValue=""`는 안전장치가 아니라 빈 URL을 주입할 뿐이고,
  Loki4jAppender가 `URI with undefined scheme`으로 **로깅 초기화 단계에서 앱을 죽인다**(DB에 도달조차 못 함).
  → **"관측 설정 하나가 빠졌다고 앱 전체가 못 뜨는 것" 자체가 결함**이므로 prod에도 이득. BE 코드 변경.
- `[결정]` **시크릿은 출처별로 2개 분리.** `core-api-db`(AWS 소유 — RDS 관리형 크리덴셜 + 엔드포인트) /
  `core-api-app`(내가 넣는 값 — JWT·GitHub OAuth). 소유자와 로테이션 주기가 다르므로 한 덩어리로
  묶지 않는다. Stage 3에서 RDS→in-cluster Postgres로 갈아낄 때 db쪽만 교체하면 되는 이점도 있다.
  대가는 `k8s/base/core-api.yaml`의 envFrom을 2개로 늘리는 것(1줄씩).
- `[메모]` **ESO 실측**(클러스터 없이 `helm template`으로 검증, 무과금):
  차트 `external-secrets/external-secrets` **v2.8.0**이 **Helm 4.2.3에서 정상 렌더**(44 리소스, 33,542줄).
  API 버전은 **`external-secrets.io/v1`** (v1beta1도 served되지만 v1이 storage). CRD 44개 중 대부분은
  generators(v1alpha1). Deployment 3개(controller·webhook·cert-controller) 전부 **resources.requests 없음**
  = BestEffort QoS. t4g.small 파드 상한은 **11**(3 ENI × (4−1) + 2, `describe-instance-types` 실측)이고
  현재 사용 5(aws-node·kube-proxy·coredns×2·core-api) + ESO 3 = **8, 여유 3**.
- `[메모]` `2-cluster`는 **CI에서 `tofu plan`이 돌지 않는다**(`infra-deploy.yml` 매트릭스가
  `[0-bootstrap, 1-network]`뿐). RDS 코드의 문법·참조 오류는 **로컬 plan에서만** 잡힌다 —
  CI 초록불이 2-cluster 코드가 유효하다는 뜻이 아니다.
- `[해결]` **무과금 구현 완료.** 작성물: `2-cluster/{rds.tf,secrets.tf,irsa-eso.tf}` +
  variables/outputs/versions 확장, `k8s/eso/{secretstore,externalsecret-db,externalsecret-app}.yaml`,
  `k8s/base/core-api.yaml` envFrom 2개화, 리퍼·하트비트 훅 RDS 대응, SOP §1·§8·§9 보강.
  검증(전부 클러스터 없이): `tofu validate` Success · `tofu fmt` 적용 · YAML 파싱 4/4 ·
  ESO 차트 Helm 4 렌더 성공.
- `[막힘]` **tfsec이 `REAL_EXIT_CODE=1`로 떨어졌다 — 로컬에서 안 돌렸으면 CI에서 PR이 막혔을 것.**
  (`2-cluster`는 CI plan 대상이 아니라서 tfsec만이 유일한 자동 게이트다.)
  4건: `AVD-AWS-0177`(deletion protection)·`AVD-AWS-0176`(IAM DB auth)·`AVD-AWS-0098`×2(시크릿 CMK).
- `[해결]` **원인: ignore ID 표기법.** tfsec 1.28에서 이 규칙들은 Rego(AVD) 기반이라
  레거시 문자열 ID(`aws-rds-enable-deletion-protection` 등)로 쓰면 **조용히 무시된다** —
  ignore가 안 먹는데 에러도 안 난다. `tfsec --include-ignored --format json`으로 실제 rule_id를
  뽑아 `AVD-AWS-XXXX` 형식으로 교체. 부수 발견: 내가 쓴 ignore 중
  `aws-rds-performance-insights-encryption-customer-key`는 **애초에 발동하지 않는 규칙**이었다
  (없는 위험을 무시하는 것처럼 읽히므로 삭제).
- `[해결]` 🔴 **`AVD-AWS-0177`은 블록이 아니라 *속성 라인*에 지적을 건다.** 리소스 상단에 둔 ignore가
  안 먹어서, `deletion_protection = false` **바로 위 줄**로 옮겨야 통과했다(실측).
- `[결정]` `AVD-AWS-0176`(IAM DB 인증)은 ignore로 덮지 않고 **실제로 켰다**
  (`iam_database_authentication_enabled = true`). 추가 비용 0이고 비밀번호 인증과 배타적이지 않으며,
  나중에 "파드가 IRSA 신원으로 DB에 직접 인증"하는 확장을 코드 변경만으로 실습할 수 있게 된다.
  → 최종 `tfsec REAL_EXIT_CODE=0` / `No problems detected!` (20 passed, 25 ignored).
- `[해결]` **리퍼 회귀 검증 (목 주입).** `aws`를 목으로 바꿔 "EKS 없음 + RDS 생존" 상태를 흉내:
  ```
  [수정 전] 마커: 삭제됨 → RDS 감시 종료 = 영구 과금
            로그: "마커 있으나 클러스터 없음 — 마커 자가 청소."
  [수정 후] 마커: 유지됨 (올바름)
            로그: "⚠️ 부분 잔존 감지 — EKS는 없는데 RDS 생존 [devquest-eks-db]."
  ```
  **수정 전 코드가 실제로 이 시나리오에서 감시를 끊는다는 것을 증명하고 고쳤다.**
- `[해결]` **logback 조건부화 완료** (`fix(be)` 커밋). 부수 발견: logback 1.5.32의 **신형
  `<condition>` 요소 문법이 실제로는 동작하지 않는다** — `class` 속성을 요구하는 다른 액션에
  매핑돼 `ActionException: current model is null`로 부팅이 깨진다. 검증된 구형 `condition` 속성
  (deprecated 경고만 남음)으로 회귀했다. **"신형이 항상 낫다"가 아니라 실제로 돌려본 게 근거.**
  RED가 실제 인시던트 예외와 동일 재현:
  ```
  Caused by: java.lang.IllegalArgumentException: URI with undefined scheme
      at com.github.loki4j.logback.Loki4jAppender.start(Loki4jAppender.java:171)
  ```
  janino 의존성 추가(+1.13MB, 설정 로딩 시 1회 컴파일뿐이라 런타임 힙 영향 무시 가능).
- `[메모]` **다음 세션(과금)에서 확인할 미검증 항목 5가지**:
  ① `kubectl` 스키마 검증 — 이번엔 kubeconfig가 삭제된 클러스터를 가리켜 못 했다(YAML 파싱만 통과)
  ② ESO CRD `external-secrets.io/v1`이 실제 클러스터에서 수락되는지
  ③ IRSA `sub` 조건 일치 여부 — 불일치 시 `Not authorized to perform sts:AssumeRoleWithWebIdentity`
  ④ RDS 마스터 시크릿(`rds!db-...`)이 인스턴스 삭제와 함께 자동 정리되는지 (SOP §9에 미검증 표기)
  ⑤ RDS 생성/삭제 실소요 — SOP §1의 "45~60분" 추정 검증

---

## 2026-07-28 (Stage 2 과금 세션)

- `[메모]` 세션 시작 10:5x KST. 목표: 07-28 무과금 세션에서 짠 Stage 2(RDS + Secrets Manager
  + IRSA + ESO)를 **실제로 apply해 검증**하고 같은 세션 안에서 destroy까지. 예상 $0.45 / 45~60분.
- `[비용]` **크레딧 잔여 실측 확보.** Cost Explorer를 `RECORD_TYPE`으로 쪼개 조회:
  ```
  $ aws ce get-cost-and-usage --time-period Start=2026-07-01,End=2026-07-29 \
      --granularity MONTHLY --metrics UnblendedCost \
      --filter '{"Dimensions":{"Key":"RECORD_TYPE","Values":["Usage","Credit"]}}' \
      --group-by Type=DIMENSION,Key=RECORD_TYPE
    Usage   0.3282654451
    Credit -0.3282654047
  ```
  2026년 1~6월 사용량 0, 7월 $0.3283 → **누적 총 사용량 $0.33 = 크레딧 잔여 ≈ $199.67.**
  지금까지 EKS 세션 전부 합쳐 33센트, 크레딧의 **0.16%**.
  ⚠️ 함정: `RECORD_TYPE` 분리 없이 조회하면 크레딧이 상계돼 **순액 $0.00000004**만 나온다.
  "아직 아무것도 안 썼네"로 오독하기 딱 좋다. 실사용량을 보려면 반드시 `Usage`로 필터.
- `[메모]` 사전 점검 통과: tofu v1.12.4 / kubectl v1.36.3 / aws-cli 2.36.2 / helm v4.2.3.
  EKS 지원 버전 확인 결과 **1.33은 표준 지원이 2026-07-29(내일) 종료**, 우리 설정은 `1.36`
  (지원 2027-08-02까지)이라 안전. kubectl 1.36과 컨트롤플레인 1.36이 정확히 일치.
- `[막힘]` **apply가 SG 생성에서 조용히 멈춤.** 13:34 apply 시작 후 `aws_security_group.rds`가
  "Creating..."에서 2분 넘게 진행 없음. 완료 로그도, 에러도 없음. AWS 측 조회도 비어 있음:
  ```
  $ aws ec2 describe-security-groups --region ap-northeast-2 \
      --filters Name=group-name,Values=devquest-eks-rds --query 'SecurityGroups[].GroupId'
  (빈 결과)
  $ tofu state list      # apply 중이라 remote state는 아직 pre-apply 상태 → 0건. 중간 판단에 못 씀.
  ```
  로그·state 둘 다 무용 → **CloudTrail로 API 호출 실체 확인**.
- `[해결]` **원인: 보안그룹 description에 한글.** CloudTrail 이벤트 원문:
  ```
  errorCode        = Client.InvalidParameterValue
  groupName        = devquest-eks-rds
  groupDescription = "RDS PostgreSQL - EKS 클러스터 파드에서만 접근 허용"
  ```
  EC2 보안그룹 description 허용 문자는 `a-zA-Z0-9`와 ` ._-:/()#,@[]+=&;{}!$*` 뿐. 한글 불가.
  → `rds.tf`의 `aws_security_group.rds`와 `aws_vpc_security_group_ingress_rule.rds_from_cluster`
  description 2개를 영문으로 교체(한글 설명은 주석으로 이전).
  - ⚠️ **`lookup-events`의 요약 필드 `ErrorCode`는 `None`으로 나온다.** 요약만 보면 "정상 호출"로
    오독한다. `CloudTrailEvent` **원문 JSON**을 파싱해야 `errorCode`가 보인다.
  - **이 버그는 `tofu validate` / `tofu plan` / `tfsec`을 전부 통과한다.** AWS API가 거부하는 값은
    실제 호출 전까지 드러나지 않는다 — "plan이 깨끗하다"가 "apply가 된다"를 의미하지 않는 실례.
  - 범위 확인: `aws_secretsmanager_secret` 2개는 **한글 description으로 생성 성공**(같은 apply 안에서).
    ECR lifecycle policy도 과거 한글로 성공. 즉 제약은 **EC2 계열 한정**이지 AWS 전역이 아니다.
    IAM은 description 규칙이 `[\p{L}\p{M}\p{Z}\p{S}\p{N}\p{P}]*`라 한글 허용 —
    이번 apply의 `aws_iam_role.eso`(한글 description)가 그대로 검증 케이스가 된다.
- `[막힘]` **apply #1 결과: EXIT=1, 24 성공 / 2 실패.** OpenTofu는 리소스 에러를 즉시 찍지 않고
  **apply 종료 시점에 모아서 출력**한다 — 그래서 중간엔 "Creating..."에서 멈춘 것처럼 보였다.
  (중간 진단에 `tofu state list`도 못 쓴다: apply 중 remote state는 pre-apply 상태라 0건)
  ```
  Error: creating IAM Role (devquest-eks-eso): api error ValidationError:
    Value at 'description' failed to satisfy constraint: Member must satisfy regular
    expression pattern: [	

 -~¡-ÿ]*
  Error: creating Security Group (devquest-eks-rds): api error InvalidParameterValue:
    Value (RDS PostgreSQL - EKS 클러스터 파드에서만 접근 허용) for parameter
    GroupDescription is invalid. Character sets beyond ASCII are not supported.
  ```
- `[해결]` **예상이 틀린 지점: IAM도 한글 description 불가.** 직전 엔트리에서 "IAM은 문서상
  `[\p{L}\p{M}\p{Z}\p{S}\p{N}\p{P}]*`라 한글 허용"이라고 적었는데 **실제 API는 다른 패턴을 강제**한다:
  `[	

 -~¡-ÿ]*` = 탭·개행·ASCII 출력가능·Latin-1 보충뿐.
  한글(U+AC00~)은 범위 밖. **문서 regex ≠ API가 강제하는 regex.**
  → `irsa-eso.tf`의 role/policy description 2개도 영문화. 최종 정리:

  | 서비스 | description 한글 | 근거 |
  |---|---|---|
  | EC2 보안그룹 / 인그레스 규칙 | ❌ | `Character sets beyond ASCII are not supported` |
  | IAM 역할 / 정책 | ❌ | `ValidationError` 정규식 |
  | Secrets Manager | ✅ | 같은 apply에서 2건 생성 성공 |
  | ECR lifecycle policy | ✅ | 0-bootstrap 기존 apply 성공 |

  → "AWS는 한글 불가"가 아니라 **서비스마다 다르다**. `resource` 블록의 description만 위험하고
    `variable`/`output`의 description은 로컬 메타데이터라 무관.
- `[결정]` **GRAFANA_* 3개를 app 시크릿에 추가.** ECR 최신 이미지(07-28 09:05)가 logback 수정
  커밋 5cf76da(10:37)보다 **먼저** 빌드돼서, 구버전 로직이 이 값 없이는 부팅에 실패한다.
  이미지 재빌드(Docker 기동+Gradle, 10~15분 과금)와 저울질한 끝에 시크릿 주입을 택했다 —
  prod(Fly.io)도 이 3개를 시크릿으로 주입하므로 **원래 맞는 설계**이고, ESO 검증도 3키→6키로 두터워진다.
  값은 도달 불가 더미(127.0.0.1). 실 Grafana 스택으로 학습 로그를 보내지 않는다.
- `[메모]` **apply #2 (수정 후) 시작 13:45.** plan = 8 add / 1 destroy.
  destroy 1건은 `secretsmanager_secret_version.app` 교체(시크릿 버전은 불변 객체라 키 추가 시 재생성).
- `[해결]` **노드 파드 상한 실측 = 11.** `kubectl get node -o jsonpath='{...allocatable.pods}'` → `11`.
  지난 세션 이론 계산 `3*(4-1)+2 = 11`(t4g.small ENI 3 × IPv4 4)과 **정확히 일치**.
  시스템 파드 4(aws-node·kube-proxy·coredns×2) + ESO 3 + core-api 1 = 8/11.
- `[해결]` **미검증 ② 해소 — ESO CRD 버전.** 차트 2.8.0 실측:
  ```
  $ kubectl get crd externalsecrets.external-secrets.io -o jsonpath=...
  v1       served=true  storage=true
  v1beta1  served=false storage=false
  ```
  지난 세션 파악("v1=storage, v1beta1=served")과 다르다. **v1beta1은 served조차 아니다** →
  매니페스트를 `external-secrets.io/v1`로 쓴 것이 유일한 정답이었다.
- `[해결]` **미검증 ① 해소 — 매니페스트 서버 측 스키마 검증 통과.**
  `kubectl apply --dry-run=server`로 secretstore/externalsecret-app 둘 다 통과(지난 세션엔
  kubeconfig가 삭제된 클러스터를 가리켜 불가했음).
- `[해결]` **미검증 ③ 해소 — IRSA `sub` 조건 일치 확인.** ESO 파드에 Pod Identity Webhook이
  자동 주입한 값:
  ```
  AWS_ROLE_ARN=arn:aws:iam::<account>:role/devquest-eks-eso
  AWS_WEB_IDENTITY_TOKEN_FILE=/var/run/secrets/eks.amazonaws.com/serviceaccount/token
  volume: aws-iam-token (projected SA token)
  ```
  SecretStore 상태 `Ready=True reason=Valid msg=store validated` → **AssumeRoleWithWebIdentity 성공.**
  이 시점에 권한 정책은 아직 미부착(RDS 마스터 시크릿 ARN에 의존)이라 ExternalSecret만 실패했는데,
  그 에러가 오히려 결정적 증거다:
  ```
  User: arn:aws:sts::<account>:assumed-role/devquest-eks-eso/<session>
    is not authorized to perform: secretsmanager:GetSecretValue
    because no identity-based policy allows the action
  ```
  `assumed-role/...`로 찍혔다 = **인증(assume)은 통과, 인가(정책)에서 막힘.**
  IRSA 두 실패 모드 구분:

  | 에러 | 층 | 원인 |
  |---|---|---|
  | `Not authorized to perform sts:AssumeRoleWithWebIdentity` | 인증 | 신뢰정책 sub/aud 불일치 |
  | `AccessDeniedException ... no identity-based policy` | 인가 | 권한 정책 미부착 |

  ESO는 22초에 5회 재시도 → `refreshInterval: 1h`와 무관하게 정책 부착 즉시 자가 복구된다.
- `[해결]` **apply #2 성공 — `Apply complete! 8 added, 0 changed, 1 destroyed.` (13:45→13:50:33)**
  - **RDS 생성 실소요 ≈ 4분 50초** (SOP §1 추정 "~10분"보다 빠름 → 미검증 ⑤ 데이터 확보).
- `[막힘]` **정책은 붙었는데 `core-api-app`만 계속 `SecretSyncedError`.** 같은 롤·같은 정책인데
  `core-api-db`는 `SecretSynced=True`로 성공. 정책 문서를 직접 확인해 권한 문제를 배제:
  ```
  Resource: [ "...:secret:rds!db-73d7eb86-...-dOT1e1",
              "...:secret:devquest-eks/db-connection-NPrett",
              "...:secret:devquest-eks/app-6L2Qjr" ]     ← app 포함되어 있음
  ```
- `[해결]` **원인은 ESO의 지수 백오프.** 실패 이벤트 간격이 16s→32s→64s→128s로 벌어져서
  정책 부착 후에도 다음 재시도 차례가 안 온 것뿐이었다. 강제 동기화로 즉시 해소:
  ```
  kubectl annotate externalsecret core-api-app force-sync="$(date +%s)" --overwrite
  → SecretSynced True (16s)
  ```
  **교훈**: ExternalSecret이 실패 상태로 보일 때 "권한이 틀렸나" 의심하기 전에
  **마지막 시도 시각(`kubectl describe`의 이벤트 age)**을 먼저 본다. 백오프 대기 중일 수 있다.
- `[해결]` **시크릿 주입 완료 — K8s Secret 2개 자동 생성.** 손으로 `kubectl create secret` 하지 않았다.
  ```
  core-api-db  (4키): DB_HOST DB_NAME DB_PASSWORD DB_USERNAME
  core-api-app (6키): JWT_SECRET GITHUB_CLIENT_ID GITHUB_CLIENT_SECRET
                      GRAFANA_API_KEY GRAFANA_LOKI_INSTANCE_ID GRAFANA_LOKI_URL
  ```
  합 10키 = 지난 세션에 "7개가 아니라 10개"로 교정했던 필수 환경변수 수와 일치.
  `DB_HOST`에 포트 없음 확인(`devquest-eks-db.<...>.rds.amazonaws.com`) — `.endpoint` 대신
  `.address`를 쓴 설계가 맞았다. 포트가 붙었으면 jdbc-url이 깨졌다.
- `[막힘]` **배포 시 `latest` 태그를 잡을 뻔했다.** `imageTags[0]`가 하필 `latest`를 집어서
  레포 규칙("항상 sha 태그, latest 배포 금지")을 어길 뻔. 40자리 hex만 고르도록 수정해 재배포:
  ```
  ruby -e 'puts JSON.parse(STDIN.read).find{|t| t =~ /\A[0-9a-f]{40}\z/}'
  → 878850a8d23f5be72b0b4a76d8687d7f369212c9
  ```
  **교훈**: ECR 이미지는 태그를 여러 개 갖는다. `imageTags[0]`은 순서 보장이 없다.
- `[해결]` **🎉 Stage 2 end-to-end 성공.** core-api 파드 `Running 1/1`, 로그:
  ```
  Database: jdbc:postgresql://devquest-eks-db.<...>.ap-northeast-2.rds.amazonaws.com/devquest
            ?sslmode=require (PostgreSQL 17.10)
  Successfully validated 12 migrations
  Current version of schema "public": 12
  Started DevQuestApplicationKt in 26.331 seconds
  ```
  `/health` → **HTTP 200** `{"result":"SUCCESS","data":"DevQuest API is running"}`
  - RDS 접속이 `sslmode=require`로 TLS 적용됨(설정 안 했는데 기본값).
  - Flyway 12개 마이그레이션이 **빈 RDS에 처음부터 실행**되어 스키마가 만들어졌다.
  - 더미 Loki URL(127.0.0.1)로 인한 로그가 **0줄** — 앱을 죽이지도, 경고를 쏟지도 않았다.
- `[메모]` **Stage 1 대비 달라진 점**: Stage 1은 DB 미연결로 CrashLoopBackOff에서 끝났다.
  Stage 2는 같은 이미지가 **코드 변경 0으로** 정상 기동했다 — 바뀐 건 환경변수 주입 경로뿐.
  이것이 `application-prod.yml`을 100% 환경변수 기반으로 유지한 설계의 배당금.
- `[해결]` **teardown 완료 — `Destroy complete! Resources: 26 destroyed.` EXIT=0 (13:55:38→14:00:12, 4분 34초)**
  단계별 실측: 노드그룹 **2분 16초** · **RDS 3분 53초** · 컨트롤플레인 **2분 9초**.
- `[해결]` **SOP §8 ①번 주장 실증 — "Secret만 지우면 ESO가 즉시 재생성한다".**
  ```
  삭제 전 UID: 94fe931e-42d4-4a3f-9715-8337b3266142
  8초 후 UID : db35e78b-2fa5-44e6-9b13-d2eda253580e   ← 같은 이름의 다른 객체
  ```
  반대로 ExternalSecret(소유자)을 지우니 K8s Secret 2개도 함께 GC됐다(`NotFound`).
  → teardown 순서가 "ExternalSecret → SecretStore → 인프라"여야 하는 이유가 실측으로 확정.
- `[해결]` **미검증 ④ 해소 — RDS 관리형 시크릿은 자동 정리된다.**
  teardown 후 `list-secrets --include-planned-deletion` 결과 **완전히 빈 목록**.
  `manage_master_user_password`가 만든 `rds!db-73d7eb86-...`가 인스턴스 삭제와 함께 사라졌고
  복구창 좀비도, 이름 점유도 없다. (우리 시크릿 2개는 `recovery_window_in_days = 0` 효과)
- `[해결]` **고아 전수 검증 = 0건.** state 0 / EKS 0 / ELB 0 / 미사용 EBS 0 / NAT 0 / 실행중 EC2 0 /
  RDS 인스턴스 0 / 수동·자동 스냅샷 0 / DB 서브넷그룹 0 / Secrets Manager 0.
- `[해결]` **리퍼 마커 자가청소 동작 확인.** teardown 후 `eks-heartbeat-reminder.sh`를 직접 실행하니
  `active` 마커가 제거됐다(하트비트만 잔존). 지난 세션에 EKS→**EKS OR RDS**로 고친 생존 판정이
  "둘 다 없음" 케이스에서도 올바르게 동작. 리퍼 자동 발동 로그(`DEAD MAN`) **0건** = 사람이 정상 종료.
- `[비용]` **세션 결산.** 과금 구간 = apply 시작 13:33:37 → destroy 종료 14:00:12 = **26분 35초**.
  | 리소스 | 생존 시간 | 단가 | 소계 |
  |---|---|---|---|
  | EKS 컨트롤플레인 | ~27분 | $0.10/h | $0.045 |
  | t4g.small On-Demand ×1 | ~24분 | $0.0208/h | $0.008 |
  | RDS db.t4g.micro | ~15분 (13:45~14:00) | $0.025/h | $0.006 |
  | gp3 스토리지·시크릿 | — | 월정액 일할 | <$0.002 |
  | **합계(추정)** | | | **≈ $0.06** |
  **예상 $0.45의 1/7.** 이유: ① destroy를 미루지 않음 ② RDS가 추정 10분이 아니라 5분에 생성
  ③ 대기 시간에 문서·ESO 설치를 병렬로 처리해 벽시계 자체가 짧았다.
  크레딧 잔여 ≈ **$199.6 / $200** (Cost Explorer 반영은 ~24h 지연되므로 다음 세션에 실측 확인).
- `[메모]` **예상과 달랐던 것 3건 (문서를 고친 것들)**:
  ① IAM description은 한글 허용일 것 → **불가**(API가 문서와 다른 패턴 강제)
  ② RDS와 EKS가 병렬 생성될 것 → **직렬화됨**(RDS 보안그룹 인그레스가 클러스터 SG를 참조 →
     `클러스터 → SG → RDS` 사슬). SOP §1의 "병렬" 서술을 정정.
  ③ ESO CRD가 `v1`(storage) + `v1beta1`(served)일 것 → **v1beta1은 served조차 false**.
- `[메모]` **Stage 2 완료. 남은 후속**: ⓐ logback 수정본 이미지는 아직 ECR에 없다(PR 머지 시 CI가 빌드).
  그때 GRAFANA_* 없이도 부팅되는지 확인하면 5cf76da가 실환경에서 검증된다.
  ⓑ Stage 3에서 RDS → in-cluster Postgres StatefulSet으로 스왑해 "관리형↔자체운영" 비교.

## 2026-07-29

- `[결정]` **콘솔 스크린샷 체계 전면 폐기 — "IaC-first면 캡처가 왜 필요하냐"는 지적에서 출발.**
  사용자 질문: *"우리 어차피 IaC 기반이니까 글 작성시에도 캡쳐는 별도 필요없지 않을까?"*
  점검해보니 맞는 지적일 뿐 아니라 **더 큰 부채가 밑에 있었다**: 튜토리얼 G-1이 콘솔 클릭 5단계로
  예산 만드는 법을 가르치고 있었는데, `0-bootstrap/budget.tf`가 **이미 그 절차를 대체**한 상태였다.
  즉 **캡처는 죽은 절차의 부속품**이었다. 캡처만 지우면 "IaC로 대체된 절차를 가르치는" 모순은 남는다.
  → 캡처 + 콘솔 절차를 함께 폐기하고 IaC 절차로 교체.
- `[결정]` **코드화의 효용은 자동화가 아니라 함정 차단이었다.** 튜토리얼이 경고하던 콘솔 함정 2개가
  코드에선 **표현 자체가 불가능**해진다:

  | 콘솔 함정 | 코드 |
  |---|---|
  | 크레딧이 청구액을 가려 알림이 영영 안 울림 | `cost_types { include_credit = false }` |
  | 임계값 단위 기본이 `%` → 10/50/150이 $20/$100/$300 | `threshold_type = "ABSOLUTE_VALUE"` |

  "조심하세요"를 문서에 쓰는 것과 틀린 값을 못 쓰게 만드는 것의 차이. 문서에서 함정 해설을 지운 게
  아니라 **함정을 밟을 기회를 없앤 경위**로 다시 썼다.
- `[해결]` **Cost Anomaly Detection을 IaC로 편입** — `0-bootstrap/cost-anomaly.tf` 신규
  (`aws_ce_anomaly_monitor` DIMENSIONAL/SERVICE + `aws_ce_anomaly_subscription`).
  그동안 `TASKS.md` 상단은 "이상탐지는 `aws_ce_anomaly_monitor`로 처리"라고 적혀 있었지만
  **실제 `.tf`는 없었다**(grep 결과 `budget.tf` 단독). 문서가 코드보다 앞서 있던 상태를 해소.
- `[막힘]` **`frequency = "IMMEDIATE"`를 쓸 뻔했다 — EMAIL 구독자에겐 금지된 조합.**
  AWS 문서 확인 결과 **개별 즉시 알림(IMMEDIATE)은 SNS 구독자 전용**이고 EMAIL은 `DAILY`/`WEEKLY`만
  가능하다. 스키마에는 이 제약이 없어(`frequency`는 그냥 required string) **`validate`·`plan`·`tfsec`이
  전부 통과시킨다** — 07-28의 한글 `description` 함정과 **정확히 같은 계열**이다(API만 거부하는 제약).
  → 애초에 안전한 `DAILY`로 확정. 어느 해석이 맞든 통과하는 값을 고르는 쪽이 옳다.
- `[해결]` **프로바이더 스키마를 로컬에서 덤프해 인자 구조를 확인**했다(추측 대신 실측).
  `.terraform`에 프로바이더가 캐시돼 있어 네트워크·과금 없이 가능:
  ```bash
  tofu providers schema -json | ruby -rjson -e '...resource_schemas["aws_ce_anomaly_subscription"]...'
  ```
  → `threshold_expression`이 list 블록이고 그 안에 `dimension`/`and`/`or`/`not`이 중첩된다는 걸 확인.
- `[비용]` **이번 변경 $0.** `tofu plan`(읽기 전용) = `Plan: 2 to add, 0 to change, 0 to destroy`,
  기존 리소스 드리프트 0. 이상탐지·예산 모두 **무료 리소스**라 apply해도 과금 없음.
  로컬 apply는 하지 않았다 — `infra-deploy.yml`이 `0-bootstrap`을 **머지 시 자동 apply**하므로
  PR plan → 머지 → CI apply 흐름을 탄다.
- `[해결]` **미작성이던 "레이어 스캐폴딩" 섹션을 채웠다** — 재현 검증의 첫 번째 구멍이었다.
  07-18 일지의 검증된 명령을 근거로 닭-달걀 2단계(로컬 state apply → `backend.tf` 복원 →
  `tofu init -migrate-state -force-copy`)를 정리. 다만 우리는 코드를 점진 작성하며 여러 번 나눠
  apply했으므로 **"한 번에" 통합 순서 자체는 아직 재현해보지 않았다** — 문서에 🟡로 명시.
- `[막힘]` **크레딧 만료일이 문서마다 다르다 — 미해결.**
  `2027-01-15` (튜토리얼·SOP·`TASKS.md`, "계정 API 실측") vs `2027-07-15` (`infra/README.md`,
  일지 07-16 원본 기록 "가입 +1년"). **일지 내부에서도 어긋난다** (최상단 요약 = 01-15,
  원본 실측 항목 = 07-15). CLI로는 크레딧 만료를 조회할 수 없다
  (`aws freetier`는 프리티어 사용량만, 크레딧 목록 API 없음).
  → **추측으로 고치지 않고 그대로 뒀다.** 콘솔 Billing → Credits에서 확인 필요. `TASKS.md` TASK-6에 기록.
- `[해결]` **크레딧 만료일 모순 해소 — `2027-01-15` 확정** (사용자 콘솔 확인).
  같은 날 오전 `[막힘]`으로 올린 건을 당일 종결. `2027-07-15`는 **07-16 기록 시 "가입 +1년"이라는
  추론이 콘솔 값을 덮어쓴 오독**이었다. 정정 대상 2곳(`infra/aws-eks/README.md`, 이 일지 07-16·07-20
  항목)을 수정 — **일지는 원문을 취소선으로 남기고 정정을 병기**했다(사후 정리로 기록을 왜곡하지 않기 위해).
  🔴 **실질 함의: 크레딧 수명이 1년이 아니라 약 6개월이다.** 원래 계획 문서의 "6개월" 표기가 맞았고
  07-16의 "1년" 정정이 오히려 오류였다 — **남은 기간이 생각의 절반**이므로 Stage 3~5 세션 배분에 반영.
- `[메모]` **이 건이 남긴 교훈: "정정"이 항상 개선은 아니다.** 07-16에 계획서의 "6개월"을 실측이라며
  "1년"으로 고쳤는데 그게 오히려 오류였고, 그 뒤 13일간 두 날짜가 문서에 공존했다.
  값을 덮어쓸 때 **관측값인지 추론값인지 구분해 적었다면** 생기지 않았을 모순이다.
- `[막힘]` **머지 후 CI apply 실패 — `Limit exceeded on dimensional spend monitor creation`.**
  PR #342 머지 직후 `infra-deploy.yml`이 main에서 `0-bootstrap`을 apply하다 죽었다. 에러 원문:
  ```
  Error: creating Cost Explorer Anomaly Monitor (devquest-eks-service-monitor):
  operation error Cost Explorer: CreateAnomalyMonitor, https response error StatusCode: 400,
  api error ValidationException: Limit exceeded on dimensional spend monitor creation
  ```
  당시 상태 확인:
  ```
  $ aws ce get-anomaly-monitors --query 'AnomalyMonitors[].[MonitorName,MonitorType,MonitorDimension]'
  Default-Services-Monitor   DIMENSIONAL   SERVICE
  ```
- `[해결]` **원인: AWS가 신규 계정에 모니터·구독을 미리 만들어 둔다.** `Default-Services-Monitor` +
  `Default-Services-Subscription`(DAILY)이 이미 존재했고, **DIMENSIONAL(SERVICE) 모니터는 계정당 1개**만
  허용된다. 즉 "없는 걸 만드는" 게 아니라 **있는 걸 중복 생성**하려던 것.
  → 로컬에서 `tofu import`로 둘 다 state에 인수한 뒤 우리 값으로 갱신하는 구조로 변경.
  ```
  tofu import aws_ce_anomaly_monitor.services      <monitor-arn>
  tofu import aws_ce_anomaly_subscription.alerts   <subscription-arn>
  ```
  인수 후 plan: **모니터 = `updated in-place`(이름 변경), 구독 = `must be replaced`(`name` forces
  replacement)**. 모니터가 in-place인 게 중요하다 — replace였으면 삭제 후 생성이라 **같은 한도 에러를
  다시 밟았을 것**이다. 구독 재생성은 무료·즉시라 안전.
- `[결정]` **`import` 블록으로 코드화하지 않았다.** 리소스 UUID가 계정마다 달라 코드에 박으면 이식되지
  않고, ARN에 계정 ID가 들어가 공개 레포에 쓸 수도 없다. → 계정당 1회 사람이 하는 단계로 두고
  튜토리얼에 명시. 이식성 없는 값을 코드에 넣어 "자동화한 척"하는 것보다 정직하다.
- `[막힘]` **AWS 기본 구독의 임계값이 사실상 꺼진 값이었다 — 이번 건의 진짜 수확.**
  인수해 보니 `$100 이상 AND 40% 이상`이었다. **크레딧 총액이 $200인 계정에서 절반이 날아간 뒤에야
  울린다.** 콘솔에서 "이상탐지 켜져 있음"으로 보이지만 학습 계정 기준으로는 무용지물.
  → `$5`(예산 1단계 $10보다 낮게)로 하향. **"기본값이 있으니 됐다"가 가장 위험한 상태** —
  꺼진 것보다 나쁘다. 꺼진 건 없는 줄 알지만, 이건 있는 줄 안다.
- `[메모]` **`plan`이 못 잡는 실패의 3번째 사례다** (① 한글 `description` 문자셋 ② EMAIL+IMMEDIATE
  조합 ③ 이번 계정당 리소스 한도). 공통점: **셋 다 "이미 있는 것"이나 "AWS 쪽 제약"을 봐야만 알 수 있고,
  plan은 내 코드만 본다.** plan 초록불 = apply 성공이 아니다. 특히 **계정당 한도가 걸린 리소스**는
  apply 전에 `aws <service> list/get-*`으로 실물을 먼저 조회하는 습관이 필요하다.
- `[결정]` **구독 replace에 `create_before_destroy`를 넣지 않았다.** 이름 변경이 replace를 유발해
  destroy→create 사이 알림 공백이 생기지만 ① 인수 전환 1회성(이후 drift 0) ② 구독은 무료·즉시 재생성
  ③ 실패해도 예산 알림·리퍼가 남는 보조 그물 계층이라 판단. 반대로 `create_before_destroy`는 구독 2개가
  잠시 공존하는 상태를 만든다 — 이 계층엔 과한 방어. (QA F-2/F-3 지적으로 근거를 명문화)
- `[해결]` **튜토리얼 import 절차의 인덱스 접근을 이름 필터로 교체** (QA F-1). `AnomalyMonitors[0]`은
  신규 계정(모니터 1개)에서만 우연히 맞고, **커스텀 모니터가 있는 계정에서는 엉뚱한 리소스를 인수**해
  남의 설정을 재생성·개명해버린다. `?MonitorName=='Default-Services-Monitor'`로 특정하고, 인수 전
  목록을 눈으로 확인하는 단계를 앞에 뒀다. "다른 계정에서도 똑같이 하라"고 써놓고 인덱스에 의존한
  자기모순이었다.
- `[결정]` **설계 드리프트를 구조로 막는 장치 도입 — 1차 전수 점검 + 절차화.**
  사용자 지적: *"설계는 실제 구현시 계속 바뀔 수 있는데 바뀌는 부분에 대한 대비가 전혀 되어있지 않아
  계속 틀어진다."* 하루 만에 같은 유형이 4번 나온 것이 방증(이상탐지 갭·예산 절차·만료일·RDS 전략).
  **근본 원인**: 결정은 `CONTEXT.md`에, 재판정은 이 일지에 따로 산다. **일지는 append-only 시계열이라
  "지금 유효한 결정"을 알려주지 않는다** → CONTEXT만 읽으면 뒤집힌 결정을 유효한 것으로 읽는다.
  실제로 07-29에 "RDS 재탈락"을 근거로 잘못된 답을 했고, 07-28 재판정이 일지에만 있었던 게 원인.
- `[해결]` **1차 전수 점검 결과 3건 (D-1~D-3).**
  | ID | 등급 | 내용 |
  |---|---|---|
  | D-3 | 🔴 HIGH | `CONTEXT.md` "RDS 재탈락" 3개 사유 중 ①이 07-28에 무효화됐는데 **역참조 없음** — 실제 오답 유발 |
  | D-1 | LOW | `CLAUDE.md` 퀴즈 스킬 경로가 `.claude/` 누락 |
  | D-2 | LOW | `review-ledger.md`의 `application-prod.yml` 경로 축약형 |
  정합 확인돼 **문제 없던 축**: K8s 1.36·t4g.small·db.t4g.micro 코드값 일치, 매니페스트 4종 실재,
  크레딧 만료일(01-15) 전 문서 일치, 비용 모델 수치 일관.
- `[결정]` **결정 메타 줄 + 주장-검증 마커 도입.** 전략 결정에 한 줄을 붙인다:
  `> 📌 **D-001** · 상태 `🔄부분무효` · 영향 `경로들` · 재판정 `근거``
  **모든 결정에 붙이지 않는다** — "GC는 SerialGC다" 같은 관측 사실은 뒤집히면 그냥 틀린 것이라
  메타가 불필요. 대상은 **다르게 고를 수 있었던 전략 결정**뿐.
  코드 존재를 전제하는 서술에는 `<!-- verify: <경로> -->` — 이상탐지 갭을 잡았을 장치.
- `[막힘]` **검사기 첫 버전이 "에러는 찍히는데 exit 0"이었다.** 반증 테스트 3케이스를 돌리다 발견.
  원인: `... | while read` 파이프가 **서브셸**이라 루프 안의 `FAIL=1`이 밖으로 안 나간다.
  → `<<<` here-string으로 교체. **통과했다고 믿게 만드는 검사기는 없는 것보다 나쁘다** —
  검사기를 만들면 반드시 "일부러 깨뜨려 보는" 테스트를 함께 할 것.
- `[결정]` **CI 워크플로 `design-integrity.yml`에 경로 필터를 두지 않았다.** 문서를 안 건드린 PR이
  오히려 위험하기 때문 — **코드 파일 삭제가 문서를 깨뜨리는 경우**를 잡아야 하므로.
- `[메모]` **검사기가 못 잡는 것을 문서에 명시했다**(영향 목록 자체의 불완전성, 문서 내용과 코드의
  의미적 불일치, 문서 간 숫자 충돌). 기계가 확실히 아는 것만 검사한다 — **오탐이 나는 검사기는 곧 무시된다.**
- `[결정]` **결정 메타를 2건 추가 — 선정 기준은 "기각했다 재채택 가능한가".**
  후보 5개 중 사용자가 `D-002 상시 운영 기각` · `D-003 서비스 분해 에픽`을 선택.
  비용 모델·destroy-after-use·Free Plan은 제외 — **단가·실측값은 뒤집히면 그냥 틀린 것**이라
  전략 결정과 성격이 다르다(메타를 붙이면 관리 비용만 늘고 신호는 안 는다).
  | ID | 상태 | 왜 위험한가 |
  |---|---|---|
  | D-002 | `✅유효` | **재채택 유혹이 실재한다** — "잠깐 켜두면 편한데"가 곧 월 $122~174. 크레딧이 남아 보일 때 특히 흔들린다. 리퍼·SOP·`guard-local-layers`가 전부 이 결정의 파생물이라 뒤집히면 규율 전체가 무너짐 |
  | D-003 | `🚧진행중` | Phase 0~1만 구현, 2~3은 계획. **아직 코드가 없는 약속 2건**(vpc-cni `enableNetworkPolicy`·t4g medium 상향)이 드리프트 1순위 |
  D-003엔 "상태가 `🚧진행중`인 동안 이 블록을 **확정된 것으로 인용하지 말 것**"을 명시했다 —
  계획을 확정처럼 인용하는 것이 RDS 오답과 같은 계열의 사고다.

---

## 2026-07-30 — Stage 3a 착수 전 조회 (아직 $0, apply 없음)

- `[메모]` **착수 전에 `design-change-procedure.md` 2단계(D-001 영향 범위 조회)를 먼저 돌렸다.**
  어제 만든 절차의 첫 실사용. 목적은 "Stage 3 = 기각했던 in-cluster를 재채택"이라 절차의
  ✅**특히** 항목에 해당하기 때문. 결과적으로 **계획대로 갔으면 중간에 막혔을 것 3건**이 나왔다.
- `[막힘]` **CONTEXT.md 안에서 두 계획이 정면 충돌하고 있었다.** 코드가 아니라 문서끼리다.
  ```
  영속 레이어 절 : "EBS는 terraform이 소유하고 K8s는 static PV로 바인딩 (동적 PVC 아님)"
                  "EBS를 6개월 영속 유지한다"
  Stage 3 서술   : "StorageClass·동적 EBS 프로비저닝을 배우면서"
  README:128     : "StorageClass, 동적 EBS 프로비저닝"
  SOP §8         : "destroy 전 kubectl delete pvc --all -A 필수 (tofu state 밖 = 고아)"
  ```
  위는 동적을 **명시적으로 배제**하고 아래 셋은 동적을 **전제**한다. 데이터 수명도 정반대
  (세션 휘발 vs 6개월 영속). **영속 레이어 결정에 📌 메타 줄이 없어서** 그동안 아무도 못 잡았다
  — 어제 메타를 붙인 대상 선정(D-001~003)에서 이 블록이 빠졌던 것.
- `[결정]` **배제가 아니라 순서로 확정 — Stage 3을 3a/3b로 분할** (`D-004` 신설, 상태 `🔄부분무효`).
  | | 무엇 | 데이터 수명 | 근거 |
  |---|---|---|---|
  | 3a | StorageClass + `volumeClaimTemplates` (동적) | 세션 휘발 | "PVC가 EBS를 만든다"를 눈으로 본 뒤라야 `volumeHandle`이 뭔지 이해된다 |
  | 3b | terraform 소유 EBS + static PV | 6개월 영속 | 3a→3b 전환에서 실패 ④`claimRef` 잔존을 공짜로 만난다 |
  기각한 대안: ①동적만(영속 결정 폐기) → 실패 6종 중 ①③④⑥을 영영 안 만남
  ②static만 → StorageClass를 안 배우고, AZ 종속이 첫 세션부터 걸려 원인 분리가 어려움.
- `[막힘]` **파드 상한 초과가 예상된다 — 12 > 11.** t4g.small 상한 11(실측), 현재 8
  (시스템 4 + ESO 3 + core-api 1). 3a 추가분 = `ebs-csi-controller` 2 + `ebs-csi-node` 1 + postgres 1 = 4.
- `[해결]` apply 전에 실물 스키마를 조회해 확정했다(**"plan이 못 잡는 실패" 4번째를 예방**):
  ```
  $ aws eks describe-addon-configuration --addon-name aws-ebs-csi-driver \
      --addon-version v1.63.1-eksbuild.1 --region ap-northeast-2 --query configurationSchema
  "replicaCount":{"default":2,"description":"Number of replicas in the controller Deployment",
                  "minimum":1,"type":"integer"}
  "defaultStorageClass":{"properties":{"enabled":{"default":false,...}}}
  ```
  → `configuration_values`로 `controller.replicaCount=1` 지정 = 합계 11로 딱 맞음(여유 0).
  `defaultStorageClass.enabled`가 **기본 false**라 StorageClass는 우리가 직접 쓴다 — 학습엔 오히려 유리.
- `[메모]` `aws eks describe-addon-versions --addon-name aws-ebs-csi-driver`:
  `v1.63.1-eksbuild.1` / clusterVersion `1.36` 호환 / **`requiresIamPermissions: True`**
  → 애드온만 추가하면 안 되고 IRSA 필요. `irsa-eso.tf` 패턴 재사용.
- `[메모]` **문서가 코드보다 앞서 있던 자리 1건**: `infra/aws-eks/README.md:76`은 OpenTofu 관리
  애드온에 **EBS CSI**를 이미 적어놨으나 `addons.tf`엔 vpc-cni·kube-proxy·coredns **3개뿐**.
  이상탐지 갭(`TASKS.md`가 "코드로 처리"라 했으나 `.tf`가 없던 것)과 같은 계열. 3a가 이걸 메운다.
- `[메모]` 반대로 **잘 맞물려 있던 곳**: `secrets.tf`가 Stage 3을 미리 상정해 시크릿을 둘로 쪼개 뒀다
  (*"Stage 3에서 RDS를 in-cluster Postgres로 갈아낄 때 db쪽만 교체"*). `core-api.yaml`도
  envFrom 기반이라 앱 매니페스트 변경 0으로 갈아끼울 수 있다.

### 15:17 KST — 🔴 과금 세션 시작 (Stage 3a apply)

- `[비용]` `tofu plan` = **26 to add**. Stage 2도 26개였는데 **내용이 교체**됐다:
  RDS 4개(instance·subnet_group·security_group·ingress_rule) 빠지고
  EBS CSI 4개(addon·iam_role·attachment·random_password.postgres) 들어옴.
- `[해결]` **db_mode 토글이 먹은 것을 plan 출력으로 3중 확인**했다(apply 전 검증):
  ① RDS 4개가 생성 목록에 없음 → `count = 0`
  ② `secret_version.db_connection_incluster[0]`가 생성됨(`_rds`가 아니라)
  ③ **출력 `db_address`·`db_master_secret_arn`이 `Changes to Outputs`에 안 나타남**
     → `one(aws_db_instance.main[*]...)`이 null 반환. 조건부 리소스 출력의 관용구가 동작.
- `[비용]` 26개 중 **과금 대상은 5개뿐**:
  | 리소스 | 단가 |
  |---|---|
  | EKS 컨트롤플레인 | $0.10/h (전체의 78%) |
  | t4g.small ×1 ON_DEMAND | ~$0.021/h |
  | 퍼블릭 IPv4 ×1 | $0.005/h |
  | PVC가 만들 EBS 10GiB | $0.0013/h (**plan에 안 나옴** — K8s가 만들 것이라 state 밖) |
  | Secrets Manager ×2 | $0.0011/h |
  | **합계** | **≈ $0.13/h** — RDS가 빠져 Stage 2보다 시간당 $0.025 저렴 |
  나머지 21개(IAM·OIDC·애드온·access entry)는 전부 $0.
- `[메모]` 예상 왕복 ~40분(≈$0.09). Stage 2 대비 **RDS 생성 4분 50초가 통째로 빠진다**.

### 15:26 KST — apply 완료 (26 added, 0 changed, 0 destroyed)

- `[해결]` **실측 소요 — Stage 2보다 빨랐다.**
  | 단계 | 실측 | Stage 2 실측 |
  |---|---|---|
  | 컨트롤플레인 | **5분 48초** (15:17:58→15:23:46) | ~6분 |
  | 노드그룹 | **1분 57초** | 2분 48초 |
  | kube-proxy / vpc-cni | 54초 / 1분 4초 | 55초 |
  | coredns | **14초** | 24초 |
  | **aws-ebs-csi-driver** | **35초** | (신규) |
  | RDS | **없음** | 4분 50초 |
  → apply 전체 ~8분 40초. **RDS 4분 50초가 통째로 빠진 것이 가장 큰 차이.**
- `[해결]` **db_mode 토글이 실제로도 동작 — `tofu output`에 `db_address`·`db_master_secret_arn`이
  아예 나타나지 않았다.** plan 단계 예측과 일치. `one(리소스[*].속성)`이 조건부 출력의 정답 관용구.
- `[해결]` 🔴 **파드 상한 예측이 맞았다 — `replicaCount=1`이 없었으면 막혔다.**
  ```
  $ kubectl get nodes -o custom-columns=...,MAXPODS:.status.allocatable.pods
  ip-10-0-4-207.ap-northeast-2.compute.internal  Ready  v1.36.2-eks-bca9cf6  arm64  11  ap-northeast-2a
  $ kubectl get pods -A            # 6개
  kube-system/aws-node-8l758
  kube-system/coredns-66f898668d-bpxvr
  kube-system/coredns-66f898668d-gpg4q
  kube-system/ebs-csi-controller-977c4fcf8-dwtdr   ← **1개**. 기본값이면 2개였다
  kube-system/ebs-csi-node-p6h6d
  kube-system/kube-proxy-h96bp
  ```
  기본값(2)이었다면 7 + ESO 3 + postgres 1 + core-api 1 = **12 > 11**로 하나가 Pending에 갇혔다.
  현재 6 + 5 = **정확히 11/11**로 여유 0.
  → **`aws eks describe-addon-configuration`으로 apply 전에 스키마를 조회한 것이 값을 했다.**
    "plan이 못 잡는 실패"를 예방한 첫 사례(앞의 3건은 전부 사후에 발견했다).
- `[메모]` 애드온 버전 실측: ebs-csi `v1.63.1-eksbuild.1`(사전 조회값과 동일) ·
  coredns `v1.14.3-eksbuild.3` · kube-proxy `v1.36.0-eksbuild.13` · vpc-cni `v1.22.3-eksbuild.1`.
  EBS CSI에만 `serviceAccountRoleArn`이 붙어 있다(`.../role/devquest-eks-ebs-csi`) — IRSA 연결 확인.
- `[메모]` 노드 AZ = **ap-northeast-2a**. StorageClass가 `WaitForFirstConsumer`라
  EBS도 2a에 만들어질 것이다(= 실패 6종 ①AZ 불일치가 구조적으로 발생 불가).

### 15:28~15:30 KST — 동적 프로비저닝 실측 (Stage 3a 핵심)

- `[해결]` **ESO·IRSA가 한 번에 붙었다.** Stage 2에서 최다 실패였던 `sts:AssumeRoleWithWebIdentity`
  거부가 없었다 — 신뢰정책 `sub`를 그대로 재사용했기 때문.
  ```
  NAME           STORE                READY   REASON
  core-api-app   aws-secretsmanager   True    SecretSynced
  core-api-db    aws-secretsmanager   True    SecretSynced
  ```
  `core-api-db`가 **DB_HOST/DB_NAME/DB_USERNAME/DB_PASSWORD 4개를 한 출처에서** 받았다.
  → **`sed`로 ARN을 치환하는 Stage 2 절차가 통째로 사라졌다.** in-cluster의 실질 이득.
- `[해결]` **PVC → EBS 동적 생성 전 과정이 이벤트에 남았다** (블로그용 원문):
  ```
  Normal  WaitForFirstConsumer   waiting for first consumer to be created before binding
  Normal  Provisioning           ebs.csi.aws.com_ebs-csi-controller-977c4fcf8-dwtdr_...
  Normal  ExternalProvisioning   Waiting for a volume to be created either by the external
                                 provisioner 'ebs.csi.aws.com' or manually by ...
  Normal  ProvisioningSucceeded  Successfully provisioned volume pvc-09b2533e-...
  ```
  PVC Pending → Bound 까지 **약 11초**.
- `[해결]` **생성된 실물 EBS** — 주장 3개가 동시에 검증됨:
  ```
  vol-0c32788ebc4a95cb0 | 10 GiB | gp3 | ap-northeast-2a | Encrypted=True | in-use | 3000 IOPS
  ```
  ① 볼륨 AZ(2a) == 노드 AZ(2a) → **`WaitForFirstConsumer`가 AZ 불일치를 구조적으로 차단**
  ② StorageClass의 `encrypted: "true"`가 실제로 반영됨
  ③ 태그에 **`ebs.csi.aws.com/cluster = true`** — `AmazonEBSCSIDriverPolicy`가 조건 키로 쓰는 그것.
     "리소스 ARN이 아니라 **출처 태그**로 최소권한을 긋는다"는 irsa-ebs-csi.tf의 주석이 실물로 확인됐다
     (써놓고 확인은 안 했던 주장이다).
- `[해결]` 🔴 **고아 과금의 메커니즘을 숫자로 확인**:
  ```
  tofu state 총 리소스 : 34
    그중 EBS 볼륨      : 0     ← vol-0c327...은 AWS에 있는데 tofu는 모른다
  ```
  `tofu destroy`는 자기가 아는 것만 지운다 → **destroy 전 `kubectl delete pvc`가 필수인 이유**가
  추상적 규칙이 아니라 state 목록의 사실로 드러났다.
- `[해결]` **`lost+found` 함정이 실재했다.** PGDATA를 하위 디렉토리로 안 내렸으면 여기서 막혔다:
  ```
  $ kubectl exec postgres-0 -- ls -a /var/lib/postgresql/data
  .  ..  lost+found  pgdata
  $ echo $PGDATA
  /var/lib/postgresql/data/pgdata
  ```
  ext4 포맷 EBS를 마운트하면 `lost+found`가 생기고, postgres 엔트리포인트는 PGDATA가
  "비어있지 않다"고 판단해 initdb를 건너뛴 뒤 PG_VERSION이 없다며 죽는다.
- `[메모]` **기동 로그 — RDS와 같은 17.10**:
  ```
  starting PostgreSQL 17.10 on aarch64-unknown-linux-musl, compiled by gcc (Alpine 15.2.0)
  database system is ready to accept connections
  ```
  Stage 2 RDS의 `engine_version = 17.10`과 **메이저·마이너까지 동일** → 관리형↔자체운영 비교 조건 성립.
  타임스탬프가 KST로 찍힌다(매니페스트의 `TZ=Asia/Seoul`이 먹음).
- `[메모]` core-api 배포 시 ECR 태그 선택이 또 한 번 값을 했다 — 최신 이미지의 `imageTags`가
  `["latest", "e74147f80..."]` 순서라 `[0]`을 썼으면 **금지된 latest로 배포**됐다.
- `[비용]` 파드 **11/11** 도달(시스템4 + EBS CSI 2 + ESO 3 + postgres 1 + core-api 1). 예측과 정확히 일치.

### 15:32 KST — 🔴 GRAFANA 3키 검증 실패 — **전제가 틀렸다는 것을 찾아냈다**

- `[막힘]` core-api가 `GRAFANA_*` 3키 없이 **부팅하지 못했다.** CrashLoopBackOff.
  근본 원인 체인 끝(`Caused by` 8단계 중 마지막):
  ```
  Caused by: org.springframework.util.PlaceholderResolutionException:
    Could not resolve placeholder 'GRAFANA_API_KEY' in value "${GRAFANA_API_KEY}"
  ... 'otlpMetricsConfig' defined in URL [jar:nested:/app/app.jar/!BOOT-INF/lib/
      monitoring-0.0.1-SNAPSHOT-plain.jar!/com/devquest/monitoring/OtlpMetricsConfig.class]
  ```
- `[해결]` 🔴 **logback이 아니었다. 소비처가 둘이었고, 우리는 하나만 고쳤다.**
  | 경로 | 무엇이 요구하나 | 조건부인가 |
  |---|---|---|
  | 로깅 | `GRAFANA_LOKI_URL` → logback `<if>` | ✅ `5cf76da`로 조건부화됨 |
  | **메트릭** | **`GRAFANA_API_KEY` → `OtlpMetricsConfig` 생성자** | ❌ **키 유무와 무관하게 켜진다** |

  `OtlpMetricsConfig`에는 가드가 **있다**:
  ```kotlin
  @ConditionalOnProperty("grafana.otlp.enabled", havingValue = "true")
  class OtlpMetricsConfig(
      @Value("\${grafana.otlp.instance-id}") private val instanceId: String,
      @Value("\${GRAFANA_API_KEY}") private val apiKey: String,
  )
  ```
  그런데 그 스위치가 `application-prod.yml`에 **하드코딩 `true`**다:
  ```yaml
  grafana:
    otlp:
      enabled: true
  ```
  → 가드가 "키가 있는가"가 아니라 **"켜라고 했는가"**만 본다. prod 프로파일이면 무조건 켜지고,
  그다음 생성자 주입에서 키가 없어 컨텍스트가 죽는다.
- `[메모]` **CONTEXT에 적혀 있던 전제가 불완전했다** — *"이게 통과하면 logback 조건부화(5cf76da)의
  실환경 검증이 끝난다"*. 소비처가 하나라고 가정한 서술이었다. **실환경에 올려보지 않았으면
  계속 "검증만 남았다"고 믿었을 것.** Phase 1 회고의 *"경계를 넘는 계약은 양쪽 실물을 붙여서
  검증한다"*가 환경변수 계약에도 똑같이 적용된다.
- `[막힘]` **파드 상한 여유 0이 재배포를 막았다** — 예고했던 리스크가 그대로 실현:
  ```
  0/1 nodes are available: 1 Insufficient memory, 1 Too many pods.
  ```
  `kubectl rollout restart`는 새 파드를 먼저 띄우고 옛 파드를 지운다(RollingUpdate) →
  순간 12개 → 새 파드 Pending. **11/11은 "돌아간다"이지 "운영 가능하다"가 아니다.**
- `[해결]` 옛 파드를 직접 지워 슬롯을 비웠다. 근본 해결은 셋 중 하나:
  ① `coredns` replicaCount 1 (addons.tf에 주석으로 준비돼 있음, 슬롯 +1)
  ② `strategy: Recreate`로 바꿔 롤링 겹침 제거
  ③ t4g.medium (상한 17) — 시간당 $0.13→$0.16
  Stage 3b 착수 전 결정 필요.
- `[결정]` **BE 코드 수정은 이 세션에서 하지 않는다.** 이미지 재빌드(CI)가 필요해 과금 구간이
  길어지고, 수정은 테스트를 동반한 별도 PR이 맞다. 이번 세션은 3키를 되돌려 진행한다.

### 15:38~15:45 KST — 🔴 **관리형이 공짜로 주던 것: TLS** (Stage 3 최대 발견)

- `[막힘]` GRAFANA 3키를 되돌린 뒤에도 core-api가 죽었다. **원인이 바뀌었다**:
  ```
  Caused by: com.zaxxer.hikari.pool.HikariPool$PoolInitializationException:
    Failed to initialize pool: The server does not support SSL.
  Caused by: org.postgresql.util.PSQLException: The server does not support SSL.
  ```
- `[해결]` 원인: `application-prod.yml`의 jdbc-url이 **하드코딩**돼 있다.
  ```yaml
  jdbc-url: jdbc:postgresql://${DB_HOST}/${DB_NAME}?sslmode=require
  ```
  호스트·DB명·계정은 환경변수인데 **`sslmode=require`만 상수**다.
  RDS는 TLS가 켜진 채로 오기 때문에 이게 그냥 통했고, 그래서 아무도 몰랐다.
- `[결정]` 🔑 **"앱 코드 변경 0"이라는 Stage 3 전제가 여기서 한 번 깨졌다.**
  깨진 지점이 값지다 — **관리형이 "공짜로 주던 것"의 목록에 TLS가 있었다.**
  자동 백업·PITR·로테이션처럼 눈에 띄는 항목이 아니라, **아무도 언급하지 않는 기본값**이다.
  자체운영으로 옮긴다는 건 이런 것까지 소유한다는 뜻.
- `[결정]` **인증서를 손으로 만들지 않았다.** 검토한 대안과 기각 사유:
  | 안 | 기각/채택 |
  |---|---|
  | initContainer + openssl | ❌ `postgres:17-alpine`에 openssl 없음(실측 `sh: openssl: not found`). 다른 이미지 = 검증 안 된 서드파티 의존 |
  | `kubectl create secret` 수동 | ❌ Stage 2 규칙 위반(손으로 만든 시크릿 금지) |
  | **tofu `tls_self_signed_cert` → Secrets Manager → ESO** | ✅ **Stage 2 파이프 그대로 재사용, 새 개념 0, 세션마다 자동 재생성** |
  `tls` 프로바이더가 versions.tf에 **이미 선언돼 있었다**(미사용 상태) — init 재실행도 불필요.
- `[메모]` 자기서명으로 충분한 이유: **`sslmode=require`는 암호화만 요구하고 인증서 검증을 안 한다.**
  검증하려면 `verify-ca`/`verify-full`이어야 하고 그때는 CA를 클라이언트에 심어야 한다.
  ⚠️ 즉 **이 구성은 MITM을 막지 못한다.** 실운영이면 cert-manager로 CA 체계를 세울 자리.
- `[해결]` 🔴 **PostgreSQL 키 파일 권한 검사 — 0600을 줄 수 없는 구조였다.**
  PG는 키 파일이 group/world에 열려 있으면 기동을 거부하는데, **예외가 "root 소유 + group read(0640)"**다.
  K8s Secret 볼륨은 kubelet이 `root:fsGroup` 소유로 마운트하므로 **0600(=postgres 소유)을 못 만든다.**
  → `securityContext.fsGroup: 70` + `defaultMode: 0640` 조합이 정확히 그 예외에 들어맞는다. 실측:
  ```
  $ ls -l /certs/..data/
  -rw-r-----  1 root  postgres  1294  server.crt
  -rw-r-----  1 root  postgres  1675  server.key
  $ psql -tAc "SHOW ssl;"
  on
  ```
- `[메모]` ⚠️ **YAML 8진수 함정**: `defaultMode`에 앞의 0을 빼고 `640`이라 쓰면 **10진수 640**으로
  읽혀 엉뚱한 권한이 된다. kubectl은 YAML 1.1 파서라 `0640`이 8진수. YAML 1.2식 `0o640`은
  문자열로 잡힐 수 있어 쓰지 않는다.
- `[해결]` ⭐ **StatefulSet의 약속이 증명됐다.** TLS 적용으로 파드가 재생성됐는데:
  ```
  PVC data-postgres-0 → pvc-09b2533e-178e-4380-8197-1d9c2884f989 (생성 15:29:32, 그대로)
  ```
  **파드는 죽고 새로 떴는데 볼륨은 같다.** Deployment였다면 보장되지 않는 것.
- `[해결]` core-api 재생성은 `rollout restart` 대신 **`scale 0 → 1`**로 했다.
  파드 상한 여유가 0이라 롤링(새 파드 먼저 생성)이 구조적으로 불가능하기 때문.

### 15:41~15:43 KST — ✅ Stage 3a 목표 달성 + teardown

- `[해결]` **`/health` 200. Flyway 12개가 in-cluster Postgres에 적용됐다.**
  ```
  Migrating schema "public" to version "12 - create user resume"
  Successfully applied 12 migrations to schema "public", now at version v12 (execution time 00:00.161s)
  HTTP 200  {"result":"SUCCESS","data":"DevQuest API is running","error":null}
  ```
  RDS(Stage 2)에서와 **동일한 결과**를 in-cluster에서 재현 — 앱 이미지는 같은 sha.
- `[해결]` ⭐⭐ **StatefulSet + PVC의 존재 이유를 실측으로 증명했다.** 파드를 강제 삭제한 뒤:
  ```
                삭제 전                                삭제 후
  파드 UID      1ce1fe43-ab37-4691-bb98-16a7e0e32785   98974a25-a4b2-4dde-bb37-6403c9c1a8dd
  PVC 볼륨      pvc-09b2533e-178e-4380-8197-1d9c2884f989   (동일)
  질문뱅크 행   26                                      26
  ```
  **UID가 바뀌었다 = 확실히 다른 파드 객체인데, 볼륨과 데이터는 그대로다.**
  Deployment였다면 보장되지 않는 것. (26행은 문서 기록 V10 5 + V11 21 = 26과도 일치)
- `[해결]` 🔴 **고아가 생기는 정확한 지점을 눈으로 봤다.** StatefulSet을 지운 직후:
  ```
  $ kubectl delete -f k8s/base/     # statefulset·service·storageclass 삭제됨
  $ aws ec2 describe-volumes --filters "Name=tag:ebs.csi.aws.com/cluster,Values=true"
  vol-0c32788ebc4a95cb0   in-use    ← **워크로드를 지웠는데 EBS는 살아있다**
  ```
  PVC를 지우고 나서야 회수됐다:
  ```
  $ kubectl delete pvc --all -A
  [15:43:08] vol-0c32788ebc4a95cb0  deleting
  [15:43:15] 볼륨 없음 — reclaimPolicy:Delete가 회수 완료   (7초)
  ```
  → **"StatefulSet 삭제 ≠ 볼륨 삭제"**. SOP §8의 `kubectl delete pvc --all -A`가
    형식적 절차가 아니라는 것이 이 두 출력의 차이로 확정됐다.
- `[메모]` teardown 순서는 SOP §8대로: ExternalSecret → SecretStore → 워크로드 → **PVC** → tofu destroy.

### 15:45 KST — 🟢 과금 종료 · 고아 0

- `[비용]` **과금 구간 15:17:58 → 15:45 = 약 27분. ≈ $0.06** ($0.13/h 기준).
  Stage 2(26분 35초, ~$0.06)와 거의 같다 — **RDS가 빠진 만큼 TLS 삽질에 썼다.**
- `[해결]` `Destroy complete! Resources: 30 destroyed.` (26 + TLS 4)
- `[해결]` **고아 전수 검증 = 전부 0** (SOP §9):
  ```
  tofu state          : 0개
  EKS 클러스터        : (없음)
  EC2 인스턴스        : (없음)
  EBS 볼륨            : (없음)   ← PVC 삭제를 먼저 했기 때문
  로드밸런서·NAT      : (없음)
  RDS 인스턴스·스냅샷 : (없음)
  Secrets Manager     : (없음)   ← recovery_window_in_days = 0의 효과
  ```
  세션 마커도 자가청소됨(리퍼 감시 해제).
- `[메모]` **Secrets Manager가 비어 있는 것이 중요하다.** 기본값(30일 복구창)이면 시크릿 4개가
  "삭제 대기"로 남아 개당 $0.40/월씩 과금되고, 이름이 점유돼 다음 세션 apply가 실패한다.
  이번엔 시크릿이 3개(db-connection·app·**postgres-tls**)로 늘었는데 전부 즉시 소멸했다.
