package com.devquest.client.ai.support

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AiMetricsRecorderTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var recorder: AiMetricsRecorder

    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
        recorder = AiMetricsRecorder(meterRegistry)
    }

    @Test
    fun `recordCallSuccess는 devquest_ai_call_total에 status=success로 기록한다`() {
        recorder.recordCallSuccess("CareerEssayEvaluator", "claude-haiku-4-5-20251001")

        val count = meterRegistry.counter(
            "devquest.ai.call.total",
            "evaluatorType", "CareerEssayEvaluator",
            "model", "claude-haiku-4-5-20251001",
            "status", "success"
        ).count()

        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `recordCallFailure는 devquest_ai_call_total에 status=failure로 기록한다`() {
        recorder.recordCallFailure("ResumeCheckEvaluator", "claude-haiku-4-5-20251001")

        val count = meterRegistry.counter(
            "devquest.ai.call.total",
            "evaluatorType", "ResumeCheckEvaluator",
            "model", "claude-haiku-4-5-20251001",
            "status", "failure"
        ).count()

        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `recordRetry는 devquest_ai_retry_total을 증가시킨다`() {
        recorder.recordRetry("BossPackageEvaluator", "claude-sonnet-4-6")
        recorder.recordRetry("BossPackageEvaluator", "claude-sonnet-4-6")

        val count = meterRegistry.counter(
            "devquest.ai.retry.total",
            "evaluatorType", "BossPackageEvaluator",
            "model", "claude-sonnet-4-6"
        ).count()

        assertThat(count).isEqualTo(2.0)
    }

    @Test
    fun `success와 failure는 서로 다른 카운터로 집계된다`() {
        recorder.recordCallSuccess("TestEvaluator", "claude-haiku-4-5-20251001")
        recorder.recordCallFailure("TestEvaluator", "claude-haiku-4-5-20251001")

        val success = meterRegistry.counter(
            "devquest.ai.call.total",
            "evaluatorType", "TestEvaluator",
            "model", "claude-haiku-4-5-20251001",
            "status", "success"
        ).count()

        val failure = meterRegistry.counter(
            "devquest.ai.call.total",
            "evaluatorType", "TestEvaluator",
            "model", "claude-haiku-4-5-20251001",
            "status", "failure"
        ).count()

        assertThat(success).isEqualTo(1.0)
        assertThat(failure).isEqualTo(1.0)
    }
}
