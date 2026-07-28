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
