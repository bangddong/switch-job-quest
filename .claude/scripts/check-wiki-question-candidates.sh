#!/bin/sh
# SessionStart hook: mneme wiki에 질문 뱅크 미처리 concept 페이지가 임계치 이상이면 알림.
#
# mneme는 **선택적 의존**이다 — wiki가 없는 환경(레포만 클론한 사람)에서는 조용히 통과한다.
#
# 🔴 단, "wiki가 없다"와 "설정이 틀렸다"는 **구분한다** (2026-08-09 발견).
#    예전 구현은 경로를 `E:/development/wiki`로 하드코딩했는데, 개발 기기가 macOS로 옮겨간 뒤
#    그 디렉토리가 없어 **매 세션 조용히 `exit 0`** 했다. 선택적 의존의 '조용함'이 오설정까지
#    삼키면 훅이 죽은 것을 아무도 모른다 — 이 레포에서 반복된 형태다(검사가 주장보다 헐거움).
#    발견 시점의 실피해는 0이었다(미처리 0건, 임계치 5). 고치기 가장 싼 시점이라 고쳤다.
#
# 🔴 경로를 macOS로 '바꾸지' 않은 이유: mneme-mcp에 `mneme-start.bat`·`mneme-startup.vbs`가
#    있어 Windows 기기도 쓴다. 한쪽으로 못 박으면 반대편이 죽는다. 그래서 후보 순회 + 환경변수다.

SEEDED_FILE=".claude/state/question-bank-seeded.txt"
THRESHOLD=5

# ── wiki 루트 결정 ────────────────────────────────────────────────────
# ① WIKI_DIR이 명시돼 있으면 그것만 쓴다. 틀렸으면 **경고한다** — 명시했다는 건
#    쓰겠다는 뜻이므로, 조용히 넘기면 오타를 영원히 모른다.
if [ -n "${WIKI_DIR:-}" ]; then
  if [ -d "$WIKI_DIR" ]; then
    WIKI_ROOT="$WIKI_DIR"
  else
    echo "[question-bank-seed] ⚠️ WIKI_DIR이 가리키는 경로가 없습니다: $WIKI_DIR"
    echo "  (질문 뱅크 후보 검사를 건너뜁니다. 오타이거나 wiki를 옮겼는지 확인하세요.)"
    exit 0
  fi
else
  # ② 후보 순회. 기기마다 다르므로 여기서 못 찾는 건 정상 상황일 수 있다.
  WIKI_ROOT=""
  for d in "$HOME/Develop/Sources/llm-wiki" "$HOME/Develop/wiki" "E:/development/wiki"; do
    [ -d "$d" ] && { WIKI_ROOT="$d"; break; }
  done
  # ③ 하나도 없으면 mneme 없는 환경 — 조용히 통과(설계된 동작).
  [ -n "$WIKI_ROOT" ] || exit 0
fi

WIKI_DIRS="$WIKI_ROOT/tech/pages $WIKI_ROOT/ai-llm/pages"

FOUND=0
for d in $WIKI_DIRS; do
  [ -d "$d" ] && FOUND=1
done
[ "$FOUND" = "1" ] || exit 0

# ── 후보 산출 ────────────────────────────────────────────────────────
# 🔴 **wiki 루트 기준 상대경로로 비교한다.** seeded 파일이 절대경로를 담고 있으면
#    기기가 바뀌는 순간 전부 "미처리"로 잡힌다 — 실측(08-09): 경로만 교체했을 때
#    이미 시드된 14건이 전부 신규로 잡혔다. 그대로 뒀으면 질문 뱅크에 중복이 들어갔다.
CURRENT=$(find $WIKI_DIRS -name '*.md' 2>/dev/null | sed "s|^$WIKI_ROOT/||" | sort)
[ -n "$CURRENT" ] || exit 0

if [ -f "$SEEDED_FILE" ]; then
  # 레거시 절대경로 항목(`E:/development/wiki/...`)도 상대경로로 정규화해서 받아준다.
  #
  # ⚠️ 패턴은 **임시 파일**로 넘긴다. `grep -f /dev/stdin <<EOF` 로 쓰면 heredoc이
  #    파이프의 stdin을 덮어써서 **검색 입력이 0줄**이 되고, 결과가 항상 비어
  #    "미처리 없음"으로 보인다(08-09에 실제로 이렇게 짰다가 잡았다).
  #    같은 stdin을 '패턴'과 '입력' 두 용도로 쓸 수 없다.
  PATTERN_FILE=$(mktemp) || exit 0
  sed -e 's|^.*/wiki/||' -e 's|^.*/llm-wiki/||' "$SEEDED_FILE" | sort -u > "$PATTERN_FILE"
  NEW=$(printf '%s\n' "$CURRENT" | grep -v -x -F -f "$PATTERN_FILE")
  rm -f "$PATTERN_FILE"
else
  NEW="$CURRENT"
fi

COUNT=$(printf '%s\n' "$NEW" | grep -c . 2>/dev/null)

if [ "$COUNT" -ge "$THRESHOLD" ]; then
  echo "[question-bank-seed] wiki에 질문 뱅크 미처리 concept 페이지 ${COUNT}건 (임계치 ${THRESHOLD}건 이상)."
  echo "  wiki 루트: $WIKI_ROOT"
  echo "사용자에게 '/question-bank-seed 실행할까?' 제안할 것. 목록:"
  printf '%s\n' "$NEW" | head -10
fi
exit 0
