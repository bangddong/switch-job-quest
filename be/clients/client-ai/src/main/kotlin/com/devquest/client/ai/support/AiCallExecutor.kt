package com.devquest.client.ai.support

import com.devquest.core.domain.support.AiEvaluationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AiCallExecutor(
    @Value("\${devquest.ai.max-retry:3}") private val maxRetry: Int,
    private val metricsRecorder: AiMetricsRecorder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T> execute(
        evaluatorName: String = "Unknown",
        model: String = "unknown",
        action: () -> T?,
    ): T {
        AiCallContext.set(evaluatorName)
        try {
            // AiCallContext.set() 직후이므로 evaluatorName과 동일 — 중간 수정 방지용 재조회
            val evaluator = evaluatorName
            var lastException: Exception? = null
            repeat(maxRetry) { attempt ->
                if (attempt > 0) {
                    metricsRecorder.recordRetry(evaluator, model)
                }
                try {
                    val result = action()
                    if (result != null) {
                        metricsRecorder.recordCallSuccess(evaluator, model)
                        return result
                    }
                    logger.warn("AI 응답 null — 재시도 {}/{}", attempt + 1, maxRetry)
                } catch (e: Exception) {
                    lastException = e
                    logger.warn("AI 호출 실패 — 재시도 {}/{}: {}", attempt + 1, maxRetry, e.message)
                }
            }
            metricsRecorder.recordCallFailure(evaluator, model)
            throw AiEvaluationException(
                "${maxRetry}회 시도 후 최종 실패",
                lastException
            )
        } finally {
            AiCallContext.clear()
        }
    }
}
