package com.devquest.client.ai.support

import com.devquest.core.domain.support.AiEvaluationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AiCallExecutor(
    @Value("\${devquest.ai.max-retry:3}") private val maxRetry: Int
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T> execute(action: () -> T?): T {
        var lastException: Exception? = null
        repeat(maxRetry) { attempt ->
            try {
                val result = action()
                if (result != null) return result
                logger.warn("AI 응답 null — 재시도 {}/{}", attempt + 1, maxRetry)
            } catch (e: Exception) {
                lastException = e
                logger.warn("AI 호출 실패 — 재시도 {}/{}: {}", attempt + 1, maxRetry, e.message)
            }
        }
        throw AiEvaluationException(
            "${maxRetry}회 시도 후 최종 실패",
            lastException
        )
    }
}
