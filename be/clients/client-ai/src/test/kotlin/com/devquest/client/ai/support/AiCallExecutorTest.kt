package com.devquest.client.ai.support

import com.devquest.core.domain.support.AiEvaluationException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AiCallExecutorTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var metricsRecorder: AiMetricsRecorder
    private lateinit var executor: AiCallExecutor

    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
        metricsRecorder = AiMetricsRecorder(meterRegistry)
        executor = AiCallExecutor(maxRetry = 3, metricsRecorder = metricsRecorder)
        AiCallContext.clear()
    }

    @Test
    fun `action이 성공하면 결과를 반환하고 success 카운터를 증가시킨다`() {
        val result = executor.execute(
            evaluatorName = "TestEvaluator",
            model = "claude-haiku-4-5-20251001"
        ) { "success" }

        assertThat(result).isEqualTo("success")
        val successCount = meterRegistry.counter(
            "devquest.ai.call.total",
            "evaluatorType", "TestEvaluator",
            "model", "claude-haiku-4-5-20251001",
            "status", "success"
        ).count()
        assertThat(successCount).isEqualTo(1.0)
    }

    @Test
    fun `모든 재시도 실패 시 AiEvaluationException을 던지고 failure 카운터를 증가시킨다`() {
        assertThatThrownBy {
            executor.execute<String>(
                evaluatorName = "TestEvaluator",
                model = "claude-haiku-4-5-20251001"
            ) {
                throw RuntimeException("AI 호출 실패")
            }
        }.isInstanceOf(AiEvaluationException::class.java)

        val failureCount = meterRegistry.counter(
            "devquest.ai.call.total",
            "evaluatorType", "TestEvaluator",
            "model", "claude-haiku-4-5-20251001",
            "status", "failure"
        ).count()
        assertThat(failureCount).isEqualTo(1.0)
    }

    @Test
    fun `2회 실패 후 성공하면 retry 카운터가 2 증가한다`() {
        var callCount = 0

        executor.execute(
            evaluatorName = "TestEvaluator",
            model = "claude-haiku-4-5-20251001"
        ) {
            callCount++
            if (callCount < 3) null else "ok"
        }

        val retryCount = meterRegistry.counter(
            "devquest.ai.retry.total",
            "evaluatorType", "TestEvaluator",
            "model", "claude-haiku-4-5-20251001"
        ).count()
        assertThat(retryCount).isEqualTo(2.0)
    }

    @Test
    fun `action이 null을 반환하면 AiEvaluationException 발생 후 failure 카운터 증가`() {
        assertThatThrownBy {
            executor.execute<String>(
                evaluatorName = "TestEvaluator",
                model = "claude-haiku-4-5-20251001"
            ) { null }
        }.isInstanceOf(AiEvaluationException::class.java)

        val failureCount = meterRegistry.counter(
            "devquest.ai.call.total",
            "evaluatorType", "TestEvaluator",
            "model", "claude-haiku-4-5-20251001",
            "status", "failure"
        ).count()
        assertThat(failureCount).isEqualTo(1.0)
    }
}
