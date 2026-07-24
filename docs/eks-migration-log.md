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
  2-cluster 착수 직전 시작점. 만료 2027-07-15.
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

- `[메모]` 사전 점검(비용 $0): tofu v1.12.4·aws-cli 2.36.2·자격증명 `bootstrap-admin`(계정 536260290749)
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
