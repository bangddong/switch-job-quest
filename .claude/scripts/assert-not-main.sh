#!/usr/bin/env bash
# main 브랜치에서 프로젝트 파일 수정 차단
# Write/Edit PreToolUse hook — exit 2로 Claude에게 브랜치 생성을 강제함

# stdin에서 tool input JSON 읽기 (Claude Code 훅 프로토콜)
INPUT=$(cat)

if command -v jq &>/dev/null; then
  FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty' 2>/dev/null || echo "")
elif command -v python3 &>/dev/null; then
  FILE_PATH=$(echo "$INPUT" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('file_path') or d.get('tool_input',{}).get('path') or '')" 2>/dev/null || echo "")
else
  FILE_PATH=""
fi

# FILE_PATH를 파싱할 수 없으면 허용 (경로 불명 → 차단 불가)
if [ -z "$FILE_PATH" ]; then
  exit 0
fi

# Windows 절대경로(C:\... 또는 C:/...)를 Git Bash 스타일(/c/...)로 정규화
if [[ "$FILE_PATH" =~ ^[A-Za-z]:[\\/] ]]; then
  FILE_PATH=$(echo "$FILE_PATH" | tr '\\' '/')
  DRIVE="${FILE_PATH:0:1}"
  FILE_PATH="/${DRIVE,,}${FILE_PATH:2}"
fi

# git 레포 외부 경로는 브랜치와 무관 → 허용
#
# ⚠️ 문자열 prefix 비교만 하면 **심볼릭 링크에서 조용히 fail-open**한다.
#    `git rev-parse --show-toplevel`은 **물리** 경로를 주는데 도구가 **논리** 경로를 넘기면
#    레포 안인데도 "밖"으로 오판해 exit 0 — main에서 be/**까지 전부 통과한다.
#    08-12에 이 훅의 반증 테스트를 짜다 실제로 밟았다: worktree를 /tmp에 뒀더니
#    (macOS에서 /tmp → /private/tmp) 15건 중 차단돼야 할 8건이 전부 조용히 통과했다.
#    레포가 심볼릭 링크 아래 있으면 이 가드는 통째로 꺼진다 → 양쪽을 물리 경로로 맞춘다.
#    (dirname이 아직 없는 신규 중첩 경로면 cd가 실패한다 → 원본 유지, 기존 동작으로 폴백)
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo "")
if [ -n "$REPO_ROOT" ] && [ -n "$FILE_PATH" ]; then
  RESOLVED_DIR=$(cd "$(dirname "$FILE_PATH")" 2>/dev/null && pwd -P)
  if [ -n "$RESOLVED_DIR" ]; then
    FILE_PATH="$RESOLVED_DIR/$(basename "$FILE_PATH")"
  fi
  if [[ "$FILE_PATH" == /* ]] && [[ "$FILE_PATH" != "$REPO_ROOT"* ]]; then
    exit 0
  fi
elif [ -z "$REPO_ROOT" ]; then
  exit 0
fi

BRANCH=$(git branch --show-current 2>/dev/null)

# ── main에서의 예외: **gitignore되는 경로만** ───────────────────────
#
# 2026-08-12 축소. 그전엔 `.claude/` 전체가 면제였다(#41, 04-10).
#
# 당시 사유: "CONTEXT.md·TASKS.md는 매 대화 끝에 main에서 직접 업데이트해야 함".
#   그 전제는 **무효화됐다.** 07-31 규칙 변경 이후 CONTEXT.md는 작업 브랜치에서 갱신하고
#   거기서 커밋·push한다(orchestrator 9단계). 실측: 최근 60개 first-parent 커밋 중
#   PR을 안 거친 것 0건 — 면제가 쓰이지 않는다.
#
# 반면 위험은 08-11에 실재화됐다(그날 훅 12개가 mode 644에서 되살아났다):
#   🔴 훅은 git이 아니라 **워킹트리에서 실행된다.** main에서 이 파일을 고치면
#      다음 툴 호출부터 즉시 반영된다. `git commit`을 막는 훅은 없다
#      (assert-no-main-push.sh는 `git push`만 본다).
#      → `.claude/` 전체 면제는 **가드 전체를 끄는 걸 한 단계 작업으로** 만들었다.
#         PR 없이, CI 없이, 리뷰 없이. 문서(CLAUDE.md·orchestrator.md)는 줄곧
#         "`.claude/` 포함 예외 없음"이라고 말해왔다 — 훅을 문서에 맞춘다.
#
# 그럼 왜 통째로 지우지 않나: gitignore되는 런타임 파일(qa-cache/·logs/·scratch/·
#   review-cache/)은 **애초에 리뷰 대상이 아니라** 브랜치 규율이 공허하다. 불필요하게
#   성가신 가드는 우회당하고, 우회는 가드를 침식한다(같은 날 assert-no-admin.sh에서 겪음).
#
# 판정은 하드코딩 목록이 아니라 git에게 맡긴다 → .gitignore가 바뀌면 자동으로 따라간다.
if [ "$BRANCH" = "main" ] && git check-ignore -q "$FILE_PATH" 2>/dev/null; then
  exit 0
fi

if [ "$BRANCH" = "main" ]; then
  echo "⛔ main 브랜치에서 파일을 수정할 수 없습니다." >&2
  echo "" >&2
  echo "작업 브랜치를 먼저 생성하세요:" >&2
  echo "  git fetch origin main" >&2
  echo "  git checkout -b <type>/<name> origin/main" >&2
  echo "" >&2
  echo "브랜치 타입: feat / fix / chore / docs / refactor" >&2
  exit 2
fi

exit 0
