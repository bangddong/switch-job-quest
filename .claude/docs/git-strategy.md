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

## Copilot 리뷰 처리 (머지 필수 조건)

PR 생성 후 Copilot이 자동 리뷰 댓글을 남긴다. 브랜치 보호 규칙상 **모든 Copilot 댓글에 답글이 달려야** `check-copilot-review` status가 `success`로 전환되고 머지가 가능하다.

### 절차

```bash
# 1. Copilot 리뷰 댓글 조회
gh api repos/{owner}/{repo}/pulls/<PR번호>/comments \
  --jq '[.[] | select(.user.login == "copilot-pull-request-reviewer[bot]") | select(.in_reply_to_id == null) | {id: .id, path: .path, line: .line, body: .body}]'

# 2. 각 댓글에 답글 달기 (comment_id는 위에서 조회한 id)
gh api repos/{owner}/{repo}/pulls/<PR번호>/comments \
  --method POST \
  --field body="<답글 내용>" \
  --field in_reply_to=<comment_id>

# 3. 머지
gh pr merge <PR번호> --squash --delete-branch
```

### 답글 작성 규칙
- 수용: 반영했으면 `반영했습니다.` 또는 수정 내용 간략히
- 불필요: 불필요하다고 판단되면 `이 케이스는 <이유>로 해당 없습니다.` 등 이유 명시
- 답글만 달면 workflow가 자동 재평가하여 status 갱신

## CI/CD 파이프라인

| 이벤트 | be/** 변경 | fe/** 변경 |
|--------|-----------|-----------|
| PR open | BE CI (빌드+테스트) | FE CI (빌드) |
| main 머지 | BE CD (Fly.io 배포) | FE CD (Vercel 배포) |

Workflows: `.github/workflows/be-ci.yml`, `be-cd.yml`, `fe-ci.yml`, `fe-cd.yml`
