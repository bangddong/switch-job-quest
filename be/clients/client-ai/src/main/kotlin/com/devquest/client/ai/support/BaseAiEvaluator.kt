package com.devquest.client.ai.support

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.ai.chat.client.ChatClient

abstract class BaseAiEvaluator(
    protected val chatClient: ChatClient,
    protected val aiCallExecutor: AiCallExecutor
) {
    private val objectMapper = jacksonObjectMapper()

    /**
     * AI 응답에서 마크다운 코드 블록(```json ... ```)을 제거하고 Jackson으로 파싱.
     * .entity() 대신 사용 — 중첩 객체가 많은 응답에서 AI가 마크다운으로 감싸는 경우 대응.
     */
    protected fun <T> parseContent(content: String?, targetClass: Class<T>): T? {
        val raw = content?.trim() ?: return null
        val json = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return objectMapper.readValue(json, targetClass)
    }
}
