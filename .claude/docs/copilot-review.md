# Copilot 리뷰 처리 가이드

## Gate 동작 흐름

```
PR 열림 → Commit Status: pending
Copilot 리뷰 → 인라인 코멘트 있으면 → failure
Claude Code가 직접 평가 + gh api로 답글 → Gate 재평가 → success
→ mergeStateStatus: CLEAN → 일반 머지 가능 (--admin 불필요)
```

- Commit Status 방식 (SHA 직접 기록) — check_suite 독립적
- `cancel-in-progress: false` — 취소된 check run이 branch protection을 block하는 현상 방지
- GitHub Actions 자동화 방식 포기 (PR #72): `ANTHROPIC_API_KEY`/`COMMIT_PAT` Secret 복잡도 + CommonJS top-level await 제약

## 처리 절차

```bash
# 1. PR의 Copilot 코멘트 확인
gh api --paginate repos/bangddong/switch-job-quest/pulls/<PR>/comments \
  --jq '.[] | select(.in_reply_to_id == null) | {id, path, line, body}'

# 2. 코드 수정 후 커밋 (suggestion 적용 시)
git add <file> && git commit -m "fix: ..." && git push

# 3. 각 코멘트에 답글
gh api repos/bangddong/switch-job-quest/pulls/<PR>/comments/<id>/replies \
  -X POST -f body="<답글 내용>"

# 4. Copilot 리뷰가 달렸는데 gate가 pending이면 수동 트리거
gh workflow run copilot-review-evaluator.yml -f pr_number=<PR>

# 5. Gate 확인 후 머지
gh pr checks <PR>
gh pr merge <PR> --squash --delete-branch
```

## 판단 기준

- Suggestion 블록: 타당하면 코드 수정+커밋, 아니면 거절 이유 답글
- Architectural 텍스트: 타당하면 라벨 분류 이슈 생성, 아니면 거절 이유 답글
- 이슈 라벨 6종: `architecture`, `refactor`, `tech-debt`, `performance`, `security`, `test`
