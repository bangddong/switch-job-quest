package com.devquest.client.ai.support

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.ai.chat.client.ChatClient

abstract class BaseAiEvaluator(
    protected val chatClient: ChatClient,
    protected val aiCallExecutor: AiCallExecutor,
    protected val modelName: String = AiModel.HAIKU,
) {
    companion object {
        /** 메트릭 태그용 모델 이름 상수 — client-ai-anthropic.yml 설정과 동기화 필요 */
        object AiModel {
            const val HAIKU = "claude-haiku-4-5-20251001"
            const val SONNET = "claude-sonnet-4-6"
        }
    }
    private val objectMapper = jacksonObjectMapper()

    /**
     * AI 응답에서 JSON 객체를 추출하고 Jackson으로 파싱.
     * 첫 번째 '{' ~ 마지막 '}' 범위를 추출하므로 코드블록 유무·중첩 여부와 무관하게 동작.
     *
     * 기존 lazy regex(```json ... ```)는 modelAnswer 내부에 중첩 코드블록(```java...```)이 포함되면
     * 첫 ``` 에서 멈춰 JSON이 잘리는 버그가 있었음.
     */
    protected fun <T> parseContent(content: String?, targetClass: Class<T>): T? {
        val raw = content?.trim() ?: return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        val json = if (start != -1 && end != -1 && start < end) raw.substring(start, end + 1) else raw
        return objectMapper.readValue(json, targetClass)
    }
}
