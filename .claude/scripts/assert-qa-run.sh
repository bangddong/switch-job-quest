#!/usr/bin/env bash
# gh pr create 전 QA 리뷰 실행 여부 강제 확인
# chore/, docs/ 브랜치는 면제
# 마커 파일에 HEAD SHA가 기록되어 있어야 통과 (단순 존재 확인 X)

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# gh pr create 명령인지 확인
if ! echo "$COMMAND" | grep -qE "gh pr create"; then
  exit 0
fi

BRANCH=$(git branch --show-current 2>/dev/null)

# chore/ 또는 docs/ 브랜치는 QA 면제
if echo "$BRANCH" | grep -qE "^(chore|docs)/"; then
  exit 0
fi

PROJECT_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
if [ -z "$PROJECT_ROOT" ]; then
  exit 0
fi

QA_MARKER="$PROJECT_ROOT/.claude/qa-cache/$BRANCH"

if [ ! -f "$QA_MARKER" ]; then
  echo "⛔ PR 생성 차단: QA 리뷰가 실행되지 않았습니다." >&2
  echo "   qa-reviewer 에이전트를 실행하면 마커가 자동 생성됩니다." >&2
  echo "   브랜치: $BRANCH" >&2
  exit 2
fi

HEAD_SHA=$(git rev-parse HEAD 2>/dev/null)
MARKER_SHA=$(cat "$QA_MARKER" 2>/dev/null | tr -d '[:space:]')

if [ "$HEAD_SHA" != "$MARKER_SHA" ]; then
  echo "⛔ PR 생성 차단: QA가 현재 커밋 기준으로 실행되지 않았습니다." >&2
  echo "   현재 HEAD : $HEAD_SHA" >&2
  echo "   QA 실행 시점: ${MARKER_SHA:-없음}" >&2
  echo "   커밋이 추가됐거나 QA를 건너뛴 경우 — qa-reviewer를 재실행하세요." >&2
  exit 2
fi

# ── 지적 원장 게이트 ───────────────────────────────────────────────
# findings 파일의 지적이 전부 처리(triage)됐는지 검사한다.
# 목적: "QA가 지적했는데 아무도 판단 안 하고 머지되는" 누수를 기계로 막는다.
FINDINGS="$PROJECT_ROOT/.claude/qa-cache/$BRANCH.findings.md"
LEDGER="$PROJECT_ROOT/.claude/review-ledger.md"

if [ ! -f "$FINDINGS" ]; then
  # 구버전 브랜치 호환 — SHA 마커만으로 통과시키되 경고한다.
  echo "⚠️  findings 파일 없음($BRANCH). qa-reviewer가 구버전 방식으로 돈 것 같습니다." >&2
  echo "✅ QA 확인됨 (branch=$BRANCH, sha=${HEAD_SHA:0:8}). PR 생성 진행합니다." >&2
  exit 0
fi

# 표 본문 행만 추출: | F-1 | HIGH | open | ... |
rows() { grep -E '^\|[[:space:]]*F-[0-9]+[[:space:]]*\|' "$FINDINGS" 2>/dev/null; }
field() { echo "$1" | awk -F'|' -v n="$2" '{gsub(/^[ \t]+|[ \t]+$/,"",$n); print $n}'; }

BLOCKED=0

# (1) 미처리(open/not-fixed) 지적이 남아 있으면 차단 — 등급 무관.
#     "판단을 안 한 것"과 "판단해서 안 고치기로 한 것"은 다르다. 후자만 통과시킨다.
while IFS= read -r row; do
  [ -z "$row" ] && continue
  id=$(field "$row" 2); grade=$(field "$row" 3); state=$(field "$row" 4)
  case "$state" in
    open|not-fixed)
      [ "$BLOCKED" -eq 0 ] && echo "⛔ PR 생성 차단: 처리되지 않은 QA 지적이 있습니다." >&2
      echo "   $id [$grade] 상태=$state" >&2
      BLOCKED=1
      ;;
  esac
done <<< "$(rows)"

# (2) HIGH는 'fixed' 또는 'obsolete'만 허용 — deferred/wontfix로 넘길 수 없다.
while IFS= read -r row; do
  [ -z "$row" ] && continue
  id=$(field "$row" 2); grade=$(field "$row" 3); state=$(field "$row" 4)
  if [ "$grade" = "HIGH" ] && [ "$state" != "fixed" ] && [ "$state" != "obsolete" ]; then
    [ "$BLOCKED" -eq 0 ] && echo "⛔ PR 생성 차단:" >&2
    echo "   $id 는 HIGH입니다. 미룰 수 없습니다 (현재 상태=$state, 허용=fixed|obsolete)." >&2
    BLOCKED=1
  fi
done <<< "$(rows)"

# (3) deferred 지적은 원장(review-ledger.md)에 등재돼 있어야 한다.
#     이게 없으면 브랜치 삭제와 함께 지적이 증발한다 — 이 게이트의 핵심 목적.
while IFS= read -r row; do
  [ -z "$row" ] && continue
  id=$(field "$row" 2); state=$(field "$row" 4)
  if [ "$state" = "deferred" ]; then
    if ! grep -qF "$BRANCH/$id" "$LEDGER" 2>/dev/null; then
      [ "$BLOCKED" -eq 0 ] && echo "⛔ PR 생성 차단:" >&2
      echo "   $id 가 deferred인데 원장에 없습니다 → .claude/review-ledger.md 에 '$BRANCH/$id' 출처로 등재하세요." >&2
      BLOCKED=1
    fi
  fi
done <<< "$(rows)"

if [ "$BLOCKED" -eq 1 ]; then
  echo "" >&2
  echo "   허용 상태: fixed / deferred(원장 등재 필수) / wontfix(근거 필수) / obsolete" >&2
  echo "   파일: .claude/qa-cache/$BRANCH.findings.md" >&2
  exit 2
fi

TOTAL=$(rows | wc -l | tr -d ' ')
echo "✅ QA 확인됨 (branch=$BRANCH, sha=${HEAD_SHA:0:8}, 지적 ${TOTAL}건 전부 처리됨). PR 생성 진행합니다." >&2
exit 0
