#!/usr/bin/env bash
# gh pr create 실행 전 Claude 사전 검토 강제화
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
# (로컬 훅 전용, application-local.yml은 .gitignore 등록된 파일)
if [ -z "$ANTHROPIC_API_KEY" ]; then
  LOCAL_YML="$PROJECT_ROOT/be/core/core-api/src/main/resources/application-local.yml"
  if [ -f "$LOCAL_YML" ]; then
    ANTHROPIC_API_KEY=$(grep -A1 "anthropic:" "$LOCAL_YML" | grep "api-key:" | head -1 | sed 's/.*api-key:[[:space:]]*//' | tr -d '[:space:]')
  fi
fi

if [ -z "$ANTHROPIC_API_KEY" ]; then
  echo "⛔ PR 사전 리뷰 실패: ANTHROPIC_API_KEY가 설정되지 않았습니다." >&2
  echo "   be/core/core-api/src/main/resources/application-local.yml에 spring.ai.anthropic.api-key 설정 필요." >&2
  exit 2
fi

echo "🔍 PR 생성 전 Claude 코드 리뷰 실행 중..." >&2

# diff 조회 (origin/main 기준, 30000자로 확대)
DIFF=$(git diff origin/main...HEAD 2>/dev/null | head -c 30000)
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

## 프로젝트 아키텍처 규칙
- BE: core-domain에 Spring 어노테이션(@Component, @Service, @Repository 등) 금지
- BE: db-core ↔ client-ai 모듈 간 직접 의존 금지
- BE: Kotlin !! (non-null assertion) 사용 금지
- FE: default export 금지 (named export만 허용)
- FE: CSS 모듈·Tailwind·styled-components 금지 (인라인 스타일만)
- FE: Context·Redux·Zustand 등 외부 상태관리 금지 (useState만)
- 테스트: Evaluator 테스트에 @Mock/@InjectMocks 금지 → RETURNS_DEEP_STUBS 패턴

## 프로젝트 도메인 컨텍스트 (판단 전 반드시 숙지)

### Spring Boot 라이프사이클
- 이 프로젝트는 Spring Boot 4.x 단일 인스턴스 앱이다.
- ApplicationContext.refresh()가 완료되어야 앱이 시작된다. 즉, 모든 @Bean 초기화는 앱 시작 전에 직렬로 완료된다.
- @PreDestroy는 ApplicationContext.close() 시점에 호출된다. 이는 refresh() 완료 후, 즉 모든 Bean이 이미 초기화된 후에만 발생한다.
- 따라서 @Bean 메서드 실행 중 @PreDestroy가 동시에 호출되는 것은 Spring 싱글톤 모델에서 구조적으로 불가능하다.
- @Configuration 클래스 내부 필드에 대해 \"Bean init ↔ destroy race condition\" 을 HIGH로 지적하는 것은 잘못된 판단이다.

### Spring MeterRegistry 통합
- MeterRegistryPostProcessor는 @Bean으로 등록된 MeterRegistry를 CompositeMeterRegistry에 추가한다.
- PushMeterRegistry(OtlpMeterRegistry 포함)는 start(ThreadFactory) 호출 후 내부 ScheduledExecutorService를 생성한다. start() 자체는 동기 완료된다.
- start() 후 Bean을 반환하는 것은 정상 패턴이다. \"비동기 초기화 미완료\" 라고 HIGH 지적하는 것은 잘못된 판단이다.

### 일반 원칙
- Spring 프레임워크가 보장하는 직렬 라이프사이클(init → use → destroy)은 일반 멀티스레드 코드와 다르다. 일반 동시성 규칙을 Spring 빈 라이프사이클에 그대로 적용하지 말 것.
- @Volatile, synchronized 등 동시성 도구는 Spring 빈 라이프사이클 내부에서는 선택적 개선(LOW)이지 필수가 아니다.

## Severity 기준 (반드시 이 기준만 사용)

### HIGH — PR 생성 차단 (실제 피해 발생)
다음 중 하나라도 해당하면 HIGH:
1. 위 아키텍처 규칙 위반 (빌드 또는 테스트를 깨뜨림)
2. 시크릿·API 키·비밀번호 하드코딩
3. 런타임에 NPE·ClassCastException 등 크래시를 **현재 코드에서 재현 가능한** 방식으로 확실히 유발하는 코드
4. SQL Injection·XSS 등 즉각적인 보안 취약점

HIGH 판정 전 자문: \"이 코드가 현재 프로덕션에서 실제로 크래시하거나 데이터를 손상시키는가?\" → NO이면 HIGH 금지.

### MEDIUM — 표시만, 차단 없음 (잠재적 문제)
- 명백한 로직 버그지만 즉각 크래시는 아닌 것
- 성능 anti-pattern (루프 내 DB 쿼리 등)
- 테스트 커버리지 부족으로 회귀 위험
- 이론적 동시성 위험 (Spring 라이프사이클 외부의 실제 멀티스레드 경쟁)

### LOW — 표시만, 차단 없음 (개선 권장)
- 코드 스타일·가독성 개선
- 설정값 외부화·하드코딩 개선 권장
- \"더 좋은 방법\"·\"확장성 고려\" 등 선택적 개선
- Spring 빈 라이프사이클 내 @Volatile·synchronized 등 방어적 동시성 도구 추가 권장

## 판단 원칙
- \"분산 환경 고려\", \"추후 확장성\", \"초기화 안전성 우려\", \"더 좋은 패턴 존재\" → HIGH 금지
- 실제로 현재 코드에서 피해가 발생하는지 확인 후에만 HIGH 판정
- Spring 프레임워크 보장 사항(직렬 라이프사이클, DI 완료 보장 등)을 무시한 이론적 위험 → HIGH 금지
- diff에 보이지 않는 코드는 존재하는 것으로 가정 (잘린 diff로 \"미구현\"이라 판단 금지)
- 같은 유형의 지적은 한 번만 (중복 나열 금지)

## 출력 형식 (반드시 준수)

## 🤖 Claude 코드 리뷰

### HIGH (PR 차단 — 반드시 수정)
- 없음 (또는 발견된 항목 — 아키텍처 위반·시크릿·즉각적 크래시·보안취약점만)

### MEDIUM (권장 수정 — 차단 없음)
- 없음 (또는 발견된 항목)

### LOW (선택적 개선 — 차단 없음)
- 없음 (또는 발견된 항목)

### PASSED
- 확인된 긍정적 항목

---
HIGH 여부: YES 또는 NO"

# 임시 파일로 JSON body 생성 (인코딩 문제 방지), 종료 시 자동 정리
TMPFILE=$(mktemp)
trap 'rm -f "$TMPFILE"' EXIT

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

if echo "$REVIEW_TEXT" | grep -q "HIGH 여부: YES"; then
  echo "⛔ PR 생성 차단: HIGH 항목이 발견되었습니다." >&2
  echo "   위 리뷰를 확인하고 수정 후 재시도하세요." >&2
  echo "   MEDIUM·LOW 항목은 차단 없음 — 머지 후 별도 처리 가능합니다." >&2
  exit 2
fi

# 통과: 캐시 저장
mkdir -p "$CACHE_DIR"
echo "$HEAD_SHA" > "$CACHE_FILE"

echo "✅ Claude 리뷰 통과. PR 생성 진행합니다." >&2
exit 0
