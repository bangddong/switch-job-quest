#!/usr/bin/env bash
# gh pr create 실행 전 Claude qa-reviewer 사전 검토 강제화
# CRITICAL 항목 없는 경우만 PR 생성 허용

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# gh pr create 명령인지 확인
if ! echo "$COMMAND" | grep -qE "gh pr create"; then
  exit 0
fi

PROJECT_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
if [ -z "$PROJECT_ROOT" ]; then
  exit 0
fi

HEAD_SHA=$(git rev-parse HEAD 2>/dev/null)
CACHE_DIR="$PROJECT_ROOT/.claude/review-cache"
CACHE_FILE="$CACHE_DIR/$HEAD_SHA"

# 캐시 hit: 이미 검토 통과된 커밋
if [ -f "$CACHE_FILE" ]; then
  exit 0
fi

# ANTHROPIC_API_KEY 확인 — 없으면 application-local.yml에서 자동 추출
# 보안 참고: application-local.yml은 .gitignore에 등록된 로컬 전용 파일로
# 버전 관리되지 않음. 키가 외부로 노출될 위험 없음.
if [ -z "$ANTHROPIC_API_KEY" ]; then
  LOCAL_YML="$PROJECT_ROOT/be/core/core-api/src/main/resources/application-local.yml"
  if [ -f "$LOCAL_YML" ]; then
    ANTHROPIC_API_KEY=$(grep -E "api-key:" "$LOCAL_YML" | grep "sk-ant" | head -1 | sed 's/.*api-key:[[:space:]]*//')
  fi
fi

if [ -z "$ANTHROPIC_API_KEY" ]; then
  echo "⛔ PR 사전 리뷰 실패: ANTHROPIC_API_KEY가 설정되지 않았습니다." >&2
  echo "   export ANTHROPIC_API_KEY=<your-key> 후 재시도하세요." >&2
  exit 2
fi

echo "🔍 PR 생성 전 Claude 코드 리뷰 실행 중..." >&2

# diff 조회 (origin/main 기준)
DIFF=$(git diff origin/main...HEAD 2>/dev/null | head -c 15000)
if [ -z "$DIFF" ]; then
  DIFF="(변경 없음)"
fi

PR_BRANCH=$(git branch --show-current)

PROMPT="당신은 Kotlin/Spring Boot + React/TypeScript 풀스택 프로젝트의 코드 리뷰어입니다.

## 리뷰 대상
브랜치: $PR_BRANCH

## 변경 diff
\`\`\`diff
$DIFF
\`\`\`

## 리뷰 규칙
- BE: core-domain에 Spring 어노테이션 금지, db-core↔client-ai 직접 의존 금지, !! 사용 금지
- FE: default export 금지, CSS모듈/Tailwind/styled-components 금지, Context/Redux/Zustand 금지
- 테스트: Evaluator 테스트에 @Mock/@InjectMocks 금지 → RETURNS_DEEP_STUBS 패턴

## 출력 형식 (반드시 준수)
다음 마크다운 형식으로만 출력하세요:

## 🤖 Claude 코드 리뷰

### CRITICAL (머지 전 필수 수정)
- 없음 (또는 발견된 항목)

### WARNING (권장 수정)
- 없음 (또는 발견된 항목)

### PASSED
- 확인된 긍정적 항목

---
CRITICAL 여부: YES 또는 NO"

# 임시 파일로 JSON body 생성 (인코딩 문제 방지)
TMPFILE=$(mktemp)
jq -n \
  --arg model "claude-haiku-4-5" \
  --arg content "$PROMPT" \
  '{"model": $model, "max_tokens": 2000, "messages": [{"role": "user", "content": $content}]}' \
  > "$TMPFILE"

RESPONSE=$(curl -s https://api.anthropic.com/v1/messages \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -H "content-type: application/json" \
  --data-binary "@$TMPFILE")
rm -f "$TMPFILE"

REVIEW_TEXT=$(echo "$RESPONSE" | jq -r '.content[0].text // empty' 2>/dev/null)

if [ -z "$REVIEW_TEXT" ]; then
  API_ERROR=$(echo "$RESPONSE" | jq -r '.error.message // "알 수 없는 오류"' 2>/dev/null)
  echo "⚠️  Claude 리뷰 API 호출 실패: $API_ERROR" >&2
  echo "   리뷰 없이 진행하려면 다시 시도하거나 사용자에게 보고하세요." >&2
  exit 2
fi

echo "" >&2
echo "$REVIEW_TEXT" | sed 's/^/  /' >&2
echo "" >&2

if echo "$REVIEW_TEXT" | grep -q "CRITICAL 여부: YES"; then
  echo "⛔ PR 생성 차단: CRITICAL 항목이 발견되었습니다." >&2
  echo "   위 리뷰를 확인하고 수정 후 재시도하세요." >&2
  exit 2
fi

# 통과: 캐시 저장
mkdir -p "$CACHE_DIR"
echo "$HEAD_SHA" > "$CACHE_FILE"

echo "✅ Claude 리뷰 통과. PR 생성 진행합니다." >&2
exit 0
