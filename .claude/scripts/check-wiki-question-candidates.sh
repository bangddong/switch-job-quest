#!/bin/sh
# SessionStart hook: mneme wiki에 질문 뱅크 미처리 concept 페이지가 임계치 이상이면 알림.
# wiki 없거나 접근 불가 시 조용히 통과 (mneme는 선택적 의존).

WIKI_DIRS="E:/development/wiki/tech/pages E:/development/wiki/ai-llm/pages"
SEEDED_FILE=".claude/state/question-bank-seeded.txt"
THRESHOLD=5

# wiki 디렉토리 하나도 없으면 종료
FOUND=0
for d in $WIKI_DIRS; do
  [ -d "$d" ] && FOUND=1
done
[ "$FOUND" = "1" ] || exit 0

CURRENT=$(find $WIKI_DIRS -name '*.md' 2>/dev/null | sort)
[ -n "$CURRENT" ] || exit 0

if [ -f "$SEEDED_FILE" ]; then
  NEW=$(printf '%s\n' "$CURRENT" | grep -v -x -F -f "$SEEDED_FILE")
else
  NEW="$CURRENT"
fi

COUNT=$(printf '%s\n' "$NEW" | grep -c . 2>/dev/null)

if [ "$COUNT" -ge "$THRESHOLD" ]; then
  echo "[question-bank-seed] wiki에 질문 뱅크 미처리 concept 페이지 ${COUNT}건 (임계치 ${THRESHOLD}건 이상)."
  echo "사용자에게 '/question-bank-seed 실행할까?' 제안할 것. 목록:"
  printf '%s\n' "$NEW" | head -10
fi
exit 0
