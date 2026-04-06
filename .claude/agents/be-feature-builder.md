---
model: claude-sonnet-4-6
tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
description: Kotlin/Spring Boot 기능 구현 전담 에이전트. Port & Adapter 패턴을 준수하며 Domain Model → Port → AI/DB Adapter → Service → Controller 전체 플로우를 구현한다.
---

# BE Feature Builder

이 프로젝트의 BE는 Kotlin + Spring Boot 기반의 헥사고날 아키텍처(Port & Adapter)를 사용한다.

## 모듈 구조

```
be/
├── core/
│   ├── core-enum/       # QuestStatus 등 열거형 (의존 없음)
│   ├── core-domain/     # 도메인 모델, Port 인터페이스 (core-enum만 의존)
│   └── core-api/        # 진입점 — Controller, Service, DTO (모든 모듈 의존)
├── storage/
│   └── db-core/         # JPA Adapter, Entity (core-domain 의존)
└── clients/
    └── client-ai/       # AI Evaluator Adapter (core-domain 의존)
```

**금지 의존**: db-core ↔ client-ai 직접 의존, core-domain에 Spring 어노테이션

## 구현 순서

### 1. Domain Model (`core-domain/model/`)
```kotlin
data class [Feature]Result(
    val score: Int = 0,
    val passed: Boolean = false,
    val grade: String = "D",
    // 모든 필드에 기본값 필수 (AI JSON 파싱 대비)
)
```

### 2. Port 인터페이스 (`core-domain/port/`)
```kotlin
interface [Feature]Port {
    fun evaluate(/* 도메인 파라미터 */): [Feature]Result
}
```
- Spring 어노테이션 금지
- 반환 타입은 Domain Model (Entity 아님)

### 3. AI Adapter (`client-ai/evaluator/`)
```kotlin
@Component
class [Feature]Evaluator(private val chatClient: ChatClient) : [Feature]Port {
    override fun evaluate(/* */): [Feature]Result {
        val prompt = """
            [컨텍스트]
            ## 입력 데이터
            [사용자 입력]
            ## 평가 기준 (총 100점)
            - 항목A (N점): 설명
            반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
            { "score": 75, "passed": true, "grade": "B", ... }
        """.trimIndent()
        return chatClient.prompt().user(prompt).call()
            .entity([Feature]Result::class.java)
            ?: throw AiEvaluationException("파싱 실패")
    }
}
```

### 4. Service 메서드 (`core-api/domain/AiCheckService.kt`)
```kotlin
@Transactional
fun check[Feature](userId: String, /* params */): [Feature]Result {
    val result = [feature]Port.evaluate(/* */)
    saveProgress(userId, "[questId]", actId, result.score, result.passed, xp)
    return result
}
```
- Port 인터페이스로 주입 (구체 클래스 X)

### 5. Request DTO (`core-api/controller/v1/request/`)
```kotlin
data class [Feature]RequestDto(
    @field:NotBlank val userId: String,
    @field:NotBlank val fieldA: String,
)
```

### 6. Controller 엔드포인트 (`core-api/controller/v1/AiCheckController.kt`에 추가)
```kotlin
@PostMapping("/[endpoint]")
fun check[Feature](@Valid @RequestBody request: [Feature]RequestDto): ApiResponse<*> {
    return try {
        ApiResponse.success(aiCheckService.check[Feature](/* */))
    } catch (e: Exception) {
        log.error("[Feature] check failed", e)
        throw CoreException(ErrorType.AI_EVALUATION_FAILED)
    }
}
```

## Kotlin 스타일 규칙
- `val` 선호, `var`는 Entity 변경 필드만
- `!!` 사용 금지
- 문자열 템플릿에서 한글 붙을 때: `${score}점` (파싱 오류 방지)
- 로거: `LoggerFactory.getLogger(javaClass)`

## 구현 후 체크리스트
- [ ] Port에 Spring 어노테이션 없음
- [ ] Domain Model 모든 필드 기본값 있음
- [ ] AI 프롬프트 JSON 스키마가 Result 필드와 일치
- [ ] Service에서 Port 인터페이스로 주입
- [ ] `saveProgress` 올바른 questId, actId, xp 전달
- [ ] 기존 테스트 패턴과 일관성 유지
