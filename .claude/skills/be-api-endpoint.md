---
description: BE REST API 엔드포인트를 추가할 때 사용. Request DTO, Controller, Service 레이어 생성 가이드.
---

# BE API 엔드포인트 추가 가이드

## 1. Request DTO

**파일**: `be/core/core-api/src/main/kotlin/com/devquest/core/api/controller/v1/request/[Name]RequestDto.kt`

```kotlin
package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class [Name]RequestDto(
    val userId: String = "",
    @field:NotBlank val fieldA: String = "",
    @field:Size(min = 1) val listField: List<String> = emptyList(),
)
```

규칙:
- `data class` + 기본값
- 검증은 `@field:` 접두사 사용 (Kotlin data class 특성)
- `userId`는 항상 포함

## 2. Controller

**파일**: `be/core/core-api/src/main/kotlin/com/devquest/core/api/controller/v1/[Feature]Controller.kt`

```kotlin
package com.devquest.core.api.controller.v1

import com.devquest.core.api.support.response.ApiResponse
import com.devquest.core.api.support.error.CoreException
import com.devquest.core.api.support.error.ErrorType
import jakarta.validation.Valid

@RestController
@RequestMapping("/api/v1/[feature]")
class [Feature]Controller(
    private val [feature]Service: [Feature]Service
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/[action]")
    fun [action](@Valid @RequestBody request: [Name]RequestDto): ApiResponse<*> {
        return try {
            val result = [feature]Service.[action](
                request.userId, request.fieldA, /* ... */
            )
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("[Action] failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }
}
```

패턴:
- `ApiResponse.success(data)` 래퍼로 응답
- 에러 시 `CoreException` throw → `ApiControllerAdvice`가 처리
- `@Valid`로 DTO 검증 활성화

## 3. Service

**파일**: `be/core/core-api/src/main/kotlin/com/devquest/core/api/service/[Feature]Service.kt`

```kotlin
package com.devquest.core.api.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class [Feature]Service(
    private val evaluatorPort: [Feature]EvaluatorPort,  // Port 인터페이스 주입
    private val progressPort: QuestProgressPort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun [action](userId: String, /* params */): [ResultModel] {
        val result = evaluatorPort.evaluate(/* params */)
        // progress 저장 로직
        log.info("... userId=$userId, score=${result.score}")
        return result
    }
}
```

규칙:
- **Port 인터페이스**를 주입 (구체 Adapter 클래스 주입 금지)
- `@Transactional` on 쓰기 메서드
- 로거: `LoggerFactory.getLogger(javaClass)`

## 4. ErrorType 추가 (필요 시)

`be/core/core-api/src/main/kotlin/com/devquest/core/api/support/error/ErrorType.kt`에:

```kotlin
enum class ErrorType(val status: HttpStatus, val code: ErrorCode, val message: String) {
    // ... 기존 값들
    NEW_ERROR(HttpStatus.BAD_REQUEST, ErrorCode.NEW_ERROR, ErrorCode.NEW_ERROR.message),
}
```

`ErrorCode`에도 대응하는 값 추가.

## 검증 체크리스트

- [ ] DTO에 `@field:` 접두사 검증 사용
- [ ] Controller에서 `ApiResponse` 래퍼 사용
- [ ] Service에서 Port 인터페이스 주입 (구체 클래스 X)
- [ ] `@Transactional` 적절히 사용
- [ ] 에러 처리 시 `CoreException` 사용
