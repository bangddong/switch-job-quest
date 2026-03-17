---
description: BE Port 인터페이스와 Adapter 구현체 쌍을 생성할 때 사용. 헥사고날 아키텍처의 핵심 패턴.
---

# BE Port & Adapter 생성 가이드

## 개요

Port는 core-domain에, Adapter는 해당 인프라 모듈(db-core, client-ai 등)에 위치한다.
Port에 Spring 어노테이션을 넣지 않는다.

## 1. Port 인터페이스 (core-domain)

**파일**: `be/core/core-domain/src/main/kotlin/com/devquest/core/domain/port/[Feature]Port.kt`

```kotlin
package com.devquest.core.domain.port

interface [Feature]EvaluatorPort {
    fun evaluate(/* 파라미터 */): [ResultModel]
}
```

규칙:
- 순수 Kotlin 인터페이스 (Spring 의존 없음)
- 반환 타입은 Domain Model (Entity 아님)
- 메서드명은 도메인 용어 사용

## 2. Domain Model (core-domain)

**파일**: `be/core/core-domain/src/main/kotlin/com/devquest/core/domain/model/[ResultModel].kt`

```kotlin
package com.devquest.core.domain.model

data class [ResultModel](
    val score: Int = 0,
    val passed: Boolean = false,
    val grade: String = "D",
    // ... 모든 프로퍼티에 기본값 제공
)
```

규칙:
- `data class` 사용
- 모든 프로퍼티에 기본값 (AI JSON 파싱 실패 대비)
- `val` 선호

## 3. AI Adapter (client-ai)

**파일**: `be/clients/client-ai/src/main/kotlin/com/devquest/client/ai/evaluator/[Feature]Evaluator.kt`

```kotlin
package com.devquest.client.ai.evaluator

import com.devquest.core.domain.model.[ResultModel]
import com.devquest.core.domain.port.[Feature]EvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class [Feature]Evaluator(
    private val chatClient: ChatClient
) : [Feature]EvaluatorPort {

    override fun evaluate(/* 파라미터 */): [ResultModel] {
        val prompt = """
            [컨텍스트 설명]

            ## 입력 데이터
            [사용자 입력 - 번호 매기기]

            ## 평가 기준 (총 100점)
            - 항목1 (N점): 설명
            - 항목2 (N점): 설명

            반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
            {
                "score": 75, "passed": true, "grade": "B",
                // ... ResultModel 필드와 일치
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity([ResultModel]::class.java)
            ?: throw RuntimeException("AI 평가 응답 파싱 실패")
    }
}
```

## 4. DB Adapter (db-core)

**파일**: `be/storage/db-core/src/main/kotlin/com/devquest/storage/db/core/adapter/[Feature]Adapter.kt`

```kotlin
package com.devquest.storage.db.core.adapter

@Component
class [Feature]Adapter(
    private val repository: [Feature]Repository
) : [Feature]Port {

    override fun findBy...(/* */): [DomainModel]? {
        return repository.findBy...(/* */)?.toDomain()
    }

    override fun save(model: [DomainModel]): [DomainModel] {
        return repository.save(model.toEntity()).toDomain()
    }

    // Entity -> Domain 확장함수
    private fun [Feature]Entity.toDomain(): [DomainModel] {
        return [DomainModel](
            id = this.id,
            // ... 필드 매핑
        )
    }

    // Domain -> Entity 변환
    private fun toEntity(model: [DomainModel]): [Feature]Entity {
        return [Feature]Entity(
            // ... 필드 매핑
        )
    }
}
```

## 검증 체크리스트

- [ ] Port에 Spring 어노테이션 없음
- [ ] Domain Model에 JPA 어노테이션 없음
- [ ] Adapter에 `@Component` 있음
- [ ] 매핑 함수 (`toDomain`, `toEntity`) 존재
- [ ] core-domain은 인프라 모듈을 모름
