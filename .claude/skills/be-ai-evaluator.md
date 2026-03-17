---
description: BE AI 평가기를 처음부터 끝까지 생성할 때 사용. Domain Model + Port + AI Adapter + Service 연결 + API 엔드포인트 전체 플로우.
---

# BE AI 평가기 전체 생성 가이드

새로운 AI 평가 기능을 추가할 때 아래 순서를 따른다.

## 생성 순서

### Step 1: Domain Model

`be/core/core-domain/src/main/kotlin/com/devquest/core/domain/model/[Feature]Result.kt`

```kotlin
data class [Feature]Result(
    val score: Int = 0,
    val passed: Boolean = false,
    val grade: String = "D",
    val summary: String = "",
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val detailedFeedback: String = "",
    // 기능별 고유 필드 추가
)
```

### Step 2: Port 인터페이스

`be/core/core-domain/src/main/kotlin/com/devquest/core/domain/port/[Feature]EvaluatorPort.kt`

```kotlin
interface [Feature]EvaluatorPort {
    fun evaluate(/* 도메인 파라미터 */): [Feature]Result
}
```

### Step 3: AI Adapter

`be/clients/client-ai/src/main/kotlin/com/devquest/client/ai/evaluator/[Feature]Evaluator.kt`

```kotlin
@Component
class [Feature]Evaluator(
    private val chatClient: ChatClient
) : [Feature]EvaluatorPort {

    override fun evaluate(/* params */): [Feature]Result {
        val prompt = buildPrompt(/* params */)
        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity([Feature]Result::class.java)
            ?: throw RuntimeException("AI 평가 응답 파싱 실패")
    }

    private fun buildPrompt(/* params */): String = """
        [1. 컨텍스트 — 이 평가가 무엇인지 설명]

        ## 입력 데이터
        [2. 사용자 입력을 번호/구조화하여 나열]

        ## 평가 기준 (총 100점)
        [3. 항목별 배점과 설명]
        - 항목A (30점): 설명
        - 항목B (30점): 설명
        - 항목C (20점): 설명
        - 항목D (20점): 설명

        반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
        [4. JSON 스키마 예시 — Result data class 필드와 정확히 일치]
        {
            "score": 75, "passed": true, "grade": "B",
            "summary": "...",
            "strengths": ["...", "..."],
            "improvements": ["...", "..."],
            "detailedFeedback": "..."
        }
    """.trimIndent()
}
```

프롬프트 규칙:
- 한국어로 평가 요청
- 채점 기준 합계 = 100점
- JSON 스키마는 Result data class와 필드명 일치
- `trimIndent()` 사용

### Step 4: Service 메서드 추가

`be/core/core-api/src/main/kotlin/com/devquest/core/api/service/AiCheckService.kt`에 추가:

```kotlin
@Transactional
fun check[Feature](userId: String, /* params */): [Feature]Result {
    val result = [feature]Evaluator.evaluate(/* params */)
    saveProgress(userId, "[questId]", [actId], result.score, result.passed, if (result.passed) ([xp] * result.score / 100) else 0)
    return result
}
```

### Step 5: DTO + Controller 엔드포인트

Request DTO + Controller 메서드 추가 (be-api-endpoint 스킬 참조).

### Step 6: FE 연결

`fe/src/features/ai-check/constants/formConfig.ts`에 폼 설정 추가:

```typescript
'[questId]': {
    label: '[한국어 라벨]',
    endpoint: '[api-endpoint]',
    fields: [
        { key: '...', label: '...', type: 'text|textarea|list', placeholder: '...' },
    ],
    transform: (v) => v,
},
```

## 검증 체크리스트

- [ ] Domain Model의 모든 필드에 기본값
- [ ] Port에 Spring 의존성 없음
- [ ] 프롬프트의 JSON 스키마가 Result 필드와 일치
- [ ] Service에서 Port 인터페이스로 주입
- [ ] saveProgress 호출에 올바른 questId, actId, xp
- [ ] FE formConfig에 등록
