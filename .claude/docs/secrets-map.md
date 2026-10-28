# 시크릿 지도 — 어디에 있고, 어떻게 꺼내고, 어떻게 바꾸나

> **목적**: 키가 새거나 만료됐을 때 **사본을 하나도 빠뜨리지 않고** 비우거나 교체하기 위해.
> 기억으로 찾지 말고 이 표로 찾는다. 2026-10-28 실조회(값 없이 이름만)로 작성.
>
> 🔴 **값은 어디에도 적지 않는다** — 이 파일은 공개 레포다. 이름·경로·절차만. 계정 ID·Grafana 슬러그·버킷 실명 금지.
> 🔴 **값을 화면에 내지 않는다** — 꺼낼 때는 소비자로 바로 파이프하거나 0600 임시 파일(스크래치패드, 직후 삭제)로. 확인은 해시·길이로.
> 🔴 **새 시크릿을 만들거나 위치를 옮기면 이 표를 같은 PR 에서 고친다.** 안 고치면 이 문서가 다음 사고의 원인이 된다.

## 1. 한눈에 — 보관소별

| 보관소 | 읽기 | 쓰기 | 들어 있는 것 |
|---|---|---|---|
| **Fly secrets** (`devquest-api`) | ❌ **write-only** (이름·digest 만) | `fly secrets set/import` → **앱 재시작 = prod 재배포** | 앱 런타임 전부 (§2) |
| **SSM `/devquest/prod/app/*`** | `aws ssm get-parameter --with-decryption` | `put-parameter --overwrite` (KMS `alias/aws/ssm` 유지) — ⚠️ Claude 는 쓰기가 막혀 있다(분류기, 10-28) → 사용자가 콘솔로 | EKS 가 ESO 로 읽는 앱 외부 키 + 로그인 키(허용 목록 `externalsecret-runtime.yaml`) |
| **SSM `/devquest/prod/db/*`** | 위와 같음 | 위와 같음 | EC2 DB 비밀번호·WireGuard·Neon 대기 — ⚠️ **EC2 역할이 읽는 곳** (prod 쓰기 자격증명 추가 금지) |
| **SSM `/devquest/ops/*`** | 위와 같음 | 위와 같음 | 운영 토큰(Cloudflare 8-C), 창 임대(비밀 아님) |
| **Secrets Manager** `events!connection/devquest-eks-window-github/*` | (EventBridge 관리) | `aws events update-connection` | EventBridge → GitHub dispatch PAT |
| **Secrets Manager** `devquest-eks/<env>/*` | 클러스터 있을 때만 | tofu (`2-cluster/secrets.tf`) | EKS DB 좌표·앱(JWT·OAuth 자리표시)·postgres TLS — **destroy 때 같이 사라진다** |
| **0-bootstrap tfstate** (S3) | `tofu output` | tofu | 환경별 JWT 키·postgres 마스터(학습) — ⚠️ state 에 **평문**(B3-5) |
| **GitHub Actions secrets** | ❌ write-only | `gh secret set` | CI 자격증명 (§3) |
| **GitHub Actions 워크플로 평문** | 레포 | PR | 비밀 아닌 빌드 값 (`fe-cd.yml` 의 `VITE_GITHUB_CLIENT_ID`) |
| **Cloudflare Worker** (`fe/wrangler.jsonc`) | 레포 | PR | **비밀 없음** — `vars`(오리진 2개)·KV id 는 공개 값 |
| **Vercel env** (FE 프로젝트) | ❌ 복호화 금지(규칙) | — | 옛 FE 빌드 값 — **V-4 에서 프로젝트째 삭제** |
| **로컬 맥** | — | — | `.claude/settings.local.json`(PGPASSWORD·Resend·Judge0 평문, gitignore) · `fe/.env.local` · 키체인 `vercel-token` · `~/.fly` 인증 |

## 2. 시크릿별 — 정본 · 사본 · 소비자 · 교체

> **정본** = 값을 다시 볼 수 있는 유일한 곳(없으면 "없음" — 교체 = 새로 발급).
> **교체 순서 원칙**: ① 정본에서 새 값 발급 → ② **사본 전부** 갱신 → ③ 소비자 재시작/재배포 확인 → ④ 옛 값 폐기. ④ 를 ② 전에 하면 장애다.

| 시크릿 | 정본 | 사본 (전부) | 소비자 | 교체 시 주의 |
|---|---|---|---|---|
| **Anthropic API 키 — 사본이 아니라 키 3개** | Anthropic 콘솔 | ① Fly `ANTHROPIC_API_KEY` = prod 키 ② SSM `/devquest/prod/app/ANTHROPIC_API_KEY` = **EKS 전용 별도 키**(TASK-17, 10-22 — 폐기를 쉽게 하려고 분리) ③ GitHub secret `ANTHROPIC_API_KEY` = 계보 🟡 미확인 | ① prod 앱 ② EKS 창(ESO) ③ `claude-batch-fix.yml` | 🔴 **서로 덮어쓰지 마라** — 유출된 그 키만 콘솔에서 폐기·재발급해 그 자리에만 넣는다. 셋을 같은 값으로 맞추면 분리가 무너진다 |
| **Judge0 (RapidAPI) 키** | RapidAPI 콘솔 | Fly `JUDGE0_API_KEY` · SSM `/devquest/prod/app/JUDGE0_API_KEY` · 로컬 `settings.local.json` | prod 앱 · EKS 창 | 사본 3곳 |
| **Resend API 키** | Resend 콘솔 | Fly `RESEND_API_KEY` · 로컬 `settings.local.json` | prod 앱(메일) | EKS 엔 없음(창 중 메일 꺼짐, B8-19) |
| **JWT 서명 키 (Fly)** | **없음** — Fly write-only | Fly `JWT_SECRET` | prod 앱 | 🔴 교체 = **전 사용자 로그아웃**(만료 30일). EKS 와 **다른 값**(U-11) |
| **JWT 서명 키 (EKS)** | 0-bootstrap `random_password.jwt_secret[env]` (tfstate) | Secrets Manager `devquest-eks/<env>/app` (창 동안) | EKS 창 | 통일(U-11) = 이 값을 Fly 로 **출력 없이** import(B7-17) |
| **GitHub OAuth client id** | GitHub → Developer settings → GitHub Apps (공개 값) | `fe-cd.yml` 빌드 env · Fly `GITHUB_CLIENT_ID` · SSM `/devquest/prod/app/GITHUB_CLIENT_ID`(String, EKS 창) · `application-local.yml`(로컬) · Vercel env | FE 로그인 URL · prod 앱 토큰 교환 | 비밀 아님. **FE 번들과 Fly 가 같아야** 로그인 된다 |
| **GitHub OAuth client secret — 키 2개** | **없음** — GitHub 은 발급 순간만 보여 줌 | ① Fly `GITHUB_CLIENT_SECRET` ② SSM `/devquest/prod/app/GITHUB_CLIENT_SECRET`(SecureString) = **EKS 전용 별도 secret**(B7-16, TASK-22) | ① prod 앱 ② EKS 창(ESO `core-api-runtime`) | 🔴 서로 덮어쓰지 마라 — 유출된 쪽만 GitHub 에서 폐기·재발급. learning 모드는 자리표시(`2-cluster/secrets.tf`). on 이 SSM client id = `fe-cd.yml` 값을 강제 |
| **EC2 Postgres 앱 비밀번호** | SSM `/devquest/prod/db/password` (EC2 user-data 가 생성) | Fly `DB_*` · 로컬 `settings.local.json` `PGPASSWORD` · EKS 창(ESO 가 SSM 직접) | prod 앱 · EKS 창 · 백업 | 교체 = EC2 에서 `ALTER ROLE` → SSM → Fly 순. **원격 쓰기는 사용자 명시 지시 필요** |
| **WireGuard 설정 (Fly↔EC2)** | `fly wireguard create` 산출물(개인키) | SSM `/devquest/prod/db/wireguard-conf` | EC2 DB 부팅 | 제거 = `fly wireguard remove personal devquest-prod-db` |
| **Neon 대기 DB URL** | Neon 콘솔 | SSM `/devquest/prod/db/neon-standby-url` | 매일 04:30 복원 | `neon-prod-coords` 는 String(비밀 아님). neondb_owner 비밀번호는 **리셋 안 하기로 결정** |
| **Grafana 3종 · Sentry DSN** | Grafana Cloud · Sentry 콘솔 | Fly `GRAFANA_*` · `SENTRY_DSN` | prod 앱 | EKS 엔 **현재 없음** — TASK-17 로 10번 직전 SSM `/devquest/prod/app/` 에 추가 예정, ESO 허용 목록(`externalsecret-runtime.yaml`)은 이미 열려 있다. 추가하면 이 행을 고친다. (학습 시절 *자리표시* 를 넣었다가 관측이 오발한 사고 때문에 자리표시는 금지) |
| **Cloudflare 토큰 — FE 배포** | **없음**(발급 순간만) | GitHub secret `CLOUDFLARE_API_TOKEN` (+ `CLOUDFLARE_ACCOUNT_ID`) | `fe-cd.yml` deploy-workers | 권한: Workers·KV Edit. 만료 프리셋 버튼으로(날짜 선택 = 하루짜리 함정) |
| **Cloudflare 토큰 — 8-C 창** | **없음** | SSM `/devquest/ops/cloudflare-token` | eks-window(DNS·KV) | 🔴 **만료 2027-01-24**. 만료 뒤 traffic=eks 창 off 는 임대 만료까지 보류 |
| **GitHub PAT — EventBridge** | **없음** | Secrets Manager `events!connection/devquest-eks-window-github/*` | 18:00 off · 매시 watchdog | 🔴 **만료 2027-01-15**. connection **이름 바꾸지 마라**(PAT 가 placeholder 로 돌아감) |
| **Fly 배포 토큰** | **없음** | GitHub secret `FLY_API_TOKEN` | `be-cd.yml` | app 범위 deploy 토큰(TASK-13). 값 출력 금지 |
| **AWS OIDC 역할 ARN** | AWS IAM | GitHub secrets `AWS_ROLE_ARN`(PR plan·읽기) · `AWS_ROLE_ARN_MAIN`(main 쓰기) | infra-deploy · eks-window · ecr-push · prod-db-backup-watch | 비밀번호는 아니지만 **계정 ID 포함** → secret 유지 |
| **예산 알림 메일** | — | GitHub secret `BUDGET_EMAIL` | infra-deploy | |
| **Vercel 토큰·ID** | Vercel | GitHub secrets `VERCEL_TOKEN`·`VERCEL_ORG_ID`·`VERCEL_PROJECT_ID` · 키체인 `vercel-token` | `fe-cd.yml` deploy(Vercel) | 🗑 **V-4 에서 전부 삭제 + 토큰 폐기**(전권·무기한, 채팅 노출 이력) |
| **`COMMIT_PAT`** | — | GitHub secret | **없음**(PR #72 이후 미사용) | 🗑 삭제 후보 |
| **postgres 마스터 (학습)** | 0-bootstrap tfstate | (in-cluster 모드에서만) | 학습 세션 | prod 와 무관 |
| **ArgoCD 초기 관리자** | 클러스터 내부(창마다 새로) | — | — | 출력·기록 금지 |

## 3. 자주 쓰는 명령 (값을 출력하지 않는 형태)

```bash
# 이름 목록 — 값 없음
gh secret list
aws ssm describe-parameters --region ap-northeast-2 --query 'Parameters[].[Name,Type]' --output text
aws secretsmanager list-secrets --region ap-northeast-2 --query 'SecretList[].Name' --output text
fly secrets list -a devquest-api           # 이름·digest 만 (값은 원래 못 읽는다)

# SSM → 소비자로 직접 (화면 출력 없음)
aws ssm get-parameter --region ap-northeast-2 --name <경로> --with-decryption \
  --query Parameter.Value --output text | <소비자 명령>

# 사본 일치 확인 — 해시 앞 12자만
aws ssm get-parameter ... --output text | shasum -a 256 | cut -c1-12
```

## 4. 시나리오별 체크리스트

| 상황 | 할 일 |
|---|---|
| **키 유출 의심** | §2 에서 그 행의 **정본에서 폐기·재발급** → 사본 열 **전부** 갱신 → 소비자 확인. 사본 하나라도 빠지면 그 소비자만 조용히 죽는다 |
| **만료 임박** | Cloudflare 8-C(2027-01-24) · GitHub PAT(2027-01-15) — 정본이 없으므로 **새로 발급 → 사본 덮어쓰기** |
| **서비스 폐기** (Vercel 등) | §2 의 🗑 행 — GitHub secret 삭제 · 로컬 사본 삭제 · 토큰 폐기 |
| **새 시크릿 추가** | 이 표에 행 추가(정본·사본·소비자) — 같은 PR |

## 5. 알려진 구조 문제 (나중에 할 것)

- **사본 다중화**: Judge0 3곳(Fly·SSM·로컬 — 같은 값) — 교체 누락 위험. (Anthropic 은 사본이 아니라 키가 셋 — §2) 정본 하나(SSM)에서 Fly 로 밀어 넣는 스크립트가 해법 후보.
- **정본 없음**: Fly 의 JWT·OAuth secret 은 아무도 다시 못 읽는다 → EKS 와 일치시킬 수 없다(U-11·B7-16). 통일 시 **AWS 쪽을 정본**으로 두고 Fly 로 import.
- `.claude/docs/deployment.md` 는 Vercel 시절 표 — 이 문서가 정본이다.
