# 멀티 에이전트 운영 지침

## 에이전트 실행 원칙

- 실행: `isolation: "worktree"` + `run_in_background: true`
- 권한: `~/.claude/settings.json`의 `permissions.allow`에 Bash, Write, Edit, Read, Glob, Grep 필수
- Claude는 오케스트레이터 — 직접 코드 작성은 에이전트에게 위임
- 에이전트가 변경 없이 종료 시에만 Claude가 직접 구현

## 병렬 에이전트 — 완료 알림 처리 순서 (필수)

병렬 에이전트 중 **하나라도 완료 알림이 오면**, 나머지 에이전트가 아직 실행 중이더라도 즉시 아래를 수행:

```bash
# 1. 완료된 에이전트의 실제 변경 파일 목록 확인
gh pr diff {PR번호} | grep "^diff --git"

# 2. 나머지 실행 중인 에이전트의 작업 범위와 겹치는 파일이 있으면
#    SendMessage로 즉시 알림
```

**겹치는 파일 발견 시 SendMessage 예시:**
```
"{파일명}은 이미 PR #{번호}에 구현됨.
해당 파일은 건너뛰고 나머지 작업만 완료할 것.
이미 완료했다면 PR을 닫고 종료."
```

> **왜 필요한가**: Sprint 3 에이전트가 Sprint 4 파일까지 구현했는데 Sprint 4 에이전트에 알리지 않아
> 동일 내용의 PR #25, #26이 중복 생성된 사례 발생.

## 에이전트 PR 리뷰 순서

1. `gh pr diff {번호} | grep "^diff --git"` — 파일 목록으로 지시 범위 초과 여부 먼저 확인
2. 범위 초과 파일 → 다른 에이전트 작업과 겹치면 SendMessage로 처리 지시
3. 핵심 파일 내용 리뷰 (Read로 직접 확인)
4. 수정 필요 시 → SendMessage로 에이전트에 재지시 (Claude 직접 수정 금지)

## 에이전트 지시문 작성 규칙

### 브랜치 시작 (필수 포함)
```bash
git fetch origin main
git checkout -b feat/xxx origin/main
```

### 작업 범위 명시 (병렬 실행 시 필수)
```
## 건드리면 안 되는 파일 (다른 에이전트 담당)
- SomeFile.kt  ← [에이전트명] 담당
```

### PR 생성 전 rebase (필수 포함)
```bash
git fetch origin main
git rebase origin/main
```

## 에이전트 재지시 (SendMessage)

에이전트 완료 후 리뷰에서 수정 필요 사항 발견 시 Claude 직접 수정 금지.
반드시 `SendMessage(to: {agentId})`로 재지시.

> **왜**: 멀티 에이전트 패턴에서 역할 분리 유지. Claude는 오케스트레이터, 코드 작업은 에이전트.
