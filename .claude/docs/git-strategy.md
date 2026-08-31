# Git & PR 전략

## 브랜치 구조

```
main                    ← 프로덕션 (자동 배포)
  ├── feat/xxx
  ├── fix/xxx
  ├── chore/xxx
  └── docs/xxx
```

## 원칙

- feature 브랜치 → PR → **Squash Merge** → main
- **main 직접 push 금지**
- PR은 CI(빌드 + 테스트) 통과 후에만 머지
- WIP 커밋은 feature 브랜치에서만, main 히스토리는 의미 있는 커밋만
- **브랜치는 항상 최신 main 기반으로 생성** (`git checkout -b feat/xxx origin/main`)
- **PR 전 rebase 필수** — main이 앞서 있으면 `git rebase origin/main` 후 push

## 브랜치 네이밍

```
feat/be-resume-evaluator
feat/fe-quest-map-ui
fix/be-company-fit-score-bug
chore/add-github-actions
```

## PR 생성 (gh CLI)

```bash
git push origin feat/xxx
gh pr create --title "feat(be): ..." --base main --body-file .github/pull_request_template.md
```

## PR Description 규칙

- `.github/pull_request_template.md` 기본 템플릿 사용 (`--body-file` 필수, 인라인 `--body` 금지)
- **`Why` 섹션**: 변경 이유가 자명하지 않을 때만 작성, 자명하면 생략
- **`fix` 타입**: Summary에 변경 내용, Why에 원인을 작성 (템플릿 주석 참고)
- Attribution 줄(`🤖 Generated with ...`) **포함하지 않음**

## 사전 코드 리뷰 (PR 생성 필수 조건)

`gh pr create` 실행 시 Claude Code PreToolUse 훅이 자동으로 diff를 리뷰한다.
**CRITICAL 항목이 있으면 PR 생성이 차단**되며, 수정 후 재시도해야 한다.

### 동작 방식

```
gh pr create 시도
  → assert-pr-reviewed.sh 실행
  → HEAD SHA 캐시 확인 (이미 통과한 커밋이면 skip)
  → 없으면 Anthropic API로 diff 리뷰
  → CRITICAL 있음 → ⛔ 차단
  → CRITICAL 없음 → ✅ 캐시 저장 후 PR 생성 진행
```

### CRITICAL 발견 시

리뷰 출력 확인 → 코드 수정 → 커밋 → `gh pr create` 재시도 (새 SHA로 재검토).

### 머지 — CI 확인 후 **자동 진행** (2026-08-31, 사용자 요청)

PR 을 올렸으면 **사용자에게 되묻지 말고** CI 를 지켜본 뒤 머지까지 간다.

```bash
gh pr checks <PR번호>                        # 전부 pass 인가
gh pr view <PR번호> --json mergeStateStatus  # CLEAN 인가
gh pr merge <PR번호> --squash --delete-branch
git checkout main && git fetch origin main && git reset --hard origin/main
```

#### 🔴 정지 조건 — 아래에 해당하면 **머지하지 말고 보고**한다

| # | 조건 | 왜 |
|:-:|---|---|
| 1 | **CI 실패** | 원인을 보고한다. **자동 재시도 금지** — 재실행으로 통과하면 플래키를 숨기는 것이다 |
| 2 | **`infra/aws-eks/**` 변경 + `tofu plan` 에 `to add`/`to destroy` 존재** | **머지 = AWS 리소스 생성/삭제.** 영속 리소스면 되돌리기 어렵고 계속 과금된다. plan 출력을 붙여 확인을 받는다 |
| 3 | `stage/eks-*` 브랜치 | 이해도 퀴즈 필수 + 과금 직결 (`assert-eks-quiz.sh` 가 이미 차단하지만 절차로도 명시) |
| 4 | 사용자가 *"내가 보겠다"* 고 말한 PR | 명시적 요청이 자동화를 이긴다 |
| 5 | **CI 15분 초과** | 멈춰서 상태를 보고한다. 무한 대기 금지 |

> 🔑 **2번의 판별은 기계적이다.** `infra-deploy.yml` 이 PR 에서 `tofu plan` 을 돌려 로그에 남긴다:
> ```bash
> gh run view <run-id> --log | grep -E "No changes|Plan: .* to add"
> ```
> `No changes` → 머지해도 AWS 에 아무 일도 안 일어난다 → **자동 머지.**
> `Plan: N to add` → 실제 자원이 생긴다 → **정지.**
>
> 실제 대비 (2026-08-31): `#399` = `Plan: 2 to add` → ECR 레포 생성됨 / `#400` = `No changes` → 무변화.

#### ⚠️ 이 레포는 **머지가 곧 배포**다

자동 머지를 켠다는 것은 자동 배포를 켠다는 뜻이다. 무엇이 도는지 알고 있어야 한다.

| 변경 경로 | main 머지 시 |
|---|---|
| `be/**` | **Fly.io prod 배포** (`be-cd.yml`) |
| `fe/**` | **Vercel prod 배포** (`fe-cd.yml`) |
| `infra/aws-eks/**` | **`tofu apply`** — `0-bootstrap`·`1-network` 만 (`2-cluster` 는 제외, 컨트롤플레인 $0.10/h) |
| `docs/`·`.claude/`·`.github/` | 없음 |

#### 머지 후 반드시 확인

1. `git log --oneline -1` 로 main 이 실제로 갱신됐는지 — **머지 명령의 출력만 믿지 않는다**
2. 배포가 도는 경로였다면 그 워크플로 결과까지 확인하고 보고한다
   (`infra-deploy` 는 `Apply complete!` 줄을 실측으로 인용할 것)

### 🔴 CONTEXT.md 갱신은 **그 PR 안에서** 한다

PR 을 만들기 **전에** `.claude/CONTEXT.md` 갱신을 같은 브랜치에 커밋한다.

- 최근 완료에 내용·날짜 추가 (PR 번호는 생성 후에 알게 되므로 그때 한 커밋 더 얹어 push 한다 — 브랜치 push 는 열린 PR 에 그대로 반영된다)
- 트랙이 바뀌었으면 `진행 중인 트랙` 한 줄

> **왜 절차에 박아두나 (2026-08-31)**: 08-28~29 에 `#396`·`#397` 두 PR 연속으로 이걸 빼먹어
> **따라잡기 전용 PR `#398`** 을 만들어야 했다. 자동 머지가 켜지면 이 누락이 더 잦아진다 —
> 머지가 빨라질수록 "나중에 하자"의 나중이 안 온다.
>
> ⚠️ 단, **파생 가능한 상태(브랜치명·열린 PR 상태)는 적지 않는다.** 07-31 에 그것 때문에
> "클린 클로즈" PR 이 24건 쌓였다. 판별 기준: **이 문장이 스스로 거짓이 되는가?**
> `열린 PR: #350 머지 대기` → 예(❌) / `#396 머지됨 · D-008 확정 · 08-28` → 아니오(✅).

## CI/CD 파이프라인

| 이벤트 | be/** 변경 | fe/** 변경 |
|--------|-----------|-----------|
| PR open | BE CI (빌드+테스트) | FE CI (빌드) |
| main 머지 | BE CD (Fly.io 배포) | FE CD (Vercel 배포) |

Workflows: `.github/workflows/be-ci.yml`, `be-cd.yml`, `fe-ci.yml`, `fe-cd.yml`
