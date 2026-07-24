#!/usr/bin/env bash
# EKS 학습 마일스톤(stage/eks-*) PR 생성 전 이해도 퀴즈 완료 강제.
# 목적: "구축은 했지만 이해는 못 한 채" 머지되는 누수를 기계로 막는다.
#       CLAUDE.md의 "학습 설명 의무"가 글(prose)로만 있어 실제로 누락된 데 대한 교정 —
#       QA 루프(assert-qa-run.sh)와 동일 철학: 안 빠뜨리려면 사람이 아니라 기계가 막는다.
#
# 대상: 브랜치명이 `stage/eks-`로 시작할 때만. 그 외 브랜치는 전부 면제(exit 0).
# 검사: docs/eks-quizzes/<브랜치를 -로>.md 가 존재하고 통과 마커가 찍혀 있는가.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# gh pr create 명령만 검사
if ! echo "$COMMAND" | grep -qE "gh pr create"; then
  exit 0
fi

BRANCH=$(git branch --show-current 2>/dev/null)

# 학습 마일스톤 브랜치(stage/eks-*)만 대상. 나머지 전부 면제.
if ! echo "$BRANCH" | grep -qE "^stage/eks-"; then
  exit 0
fi

PROJECT_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
if [ -z "$PROJECT_ROOT" ]; then
  exit 0
fi

SLUG=$(echo "$BRANCH" | tr '/' '-')
QUIZ="$PROJECT_ROOT/docs/eks-quizzes/$SLUG.md"

if [ ! -f "$QUIZ" ]; then
  echo "⛔ PR 생성 차단: EKS 학습 퀴즈가 없습니다." >&2
  echo "   이 브랜치는 학습 마일스톤(stage/eks-*)이라 구축 후 이해도 퀴즈가 필수입니다." >&2
  echo "   skills/universal/quiz.md 의 'EKS 학습 마일스톤 모드'로 퀴즈를 진행하면" >&2
  echo "   다음 파일이 생성됩니다: $QUIZ" >&2
  exit 2
fi

# 통과 마커 확인 — 파일만 만들고 안 끝낸(또는 빈 스텁) 경우 차단.
if ! grep -qE '<!-- *QUIZ-PASSED *-->' "$QUIZ" 2>/dev/null; then
  echo "⛔ PR 생성 차단: 퀴즈가 완료(통과)되지 않았습니다." >&2
  echo "   전 문제 풀이 + 틀린 문제 재검토까지 마친 뒤, 파일 맨 아래에" >&2
  echo "   '<!-- QUIZ-PASSED -->' 마커가 기록돼야 합니다: $QUIZ" >&2
  echo "   ⚠️ 미완인 채 마커만 먼저 넣지 마세요 — 그건 QA를 chore로 우회하는 것과 같습니다." >&2
  exit 2
fi

echo "✅ EKS 학습 퀴즈 확인됨 (branch=$BRANCH). PR 생성 진행합니다." >&2
exit 0
