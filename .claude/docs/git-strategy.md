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

### 머지

```bash
gh pr merge <PR번호> --squash --delete-branch
```

## CI/CD 파이프라인

| 이벤트 | be/** 변경 | fe/** 변경 |
|--------|-----------|-----------|
| PR open | BE CI (빌드+테스트) | FE CI (빌드) |
| main 머지 | BE CD (Fly.io 배포) | FE CD (Vercel 배포) |

Workflows: `.github/workflows/be-ci.yml`, `be-cd.yml`, `fe-ci.yml`, `fe-cd.yml`
