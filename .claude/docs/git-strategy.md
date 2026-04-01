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
gh pr create --title "feat(be): ..." --base main
gh pr merge --squash --delete-branch
```

## CI/CD 파이프라인

| 이벤트 | be/** 변경 | fe/** 변경 |
|--------|-----------|-----------|
| PR open | BE CI (빌드+테스트) | FE CI (빌드) |
| main 머지 | BE CD (Fly.io 배포) | FE CD (Vercel 배포) |

Workflows: `.github/workflows/be-ci.yml`, `be-cd.yml`, `fe-ci.yml`, `fe-cd.yml`
