package com.devquest.ai.adapter

import com.devquest.core.domain.model.AiCallLog
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 서비스 분해 에픽 Phase 1 Task 1.3 — AiCallLogObservabilityAdapter 단위 테스트.
 * Spring 컨텍스트 없이 SimpleMeterRegistry로 직접 검증(순수 로직 확인).
 */
class AiCallLogObservabilityAdapterTest {

    @Test
    fun `record 호출 시 ai_call_log_recorded 카운터가 태그와 함께 증가한다`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = AiCallLogObservabilityAdapter(meterRegistry)

        adapter.record(
            AiCallLog(
                evaluatorName = "BlogEvaluator",
                modelName = "claude-3-5-sonnet",
                inputTokens = 100,
                outputTokens = 50,
                cacheReadTokens = 10,
                cacheCreationTokens = 0,
                latencyMs = 250L,
                success = true,
            )
        )

        val counter = meterRegistry.get("ai.call.log.recorded")
            .tag("evaluator", "BlogEvaluator")
            .tag("model", "claude-3-5-sonnet")
            .tag("success", "true")
            .counter()

        assertThat(counter.count()).isEqualTo(1.0)
    }

    @Test
    fun `record 호출 시 ai_call_log_latency 타이머가 기록된다`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = AiCallLogObservabilityAdapter(meterRegistry)

        adapter.record(
            AiCallLog(
                evaluatorName = "BlogEvaluator",
                modelName = "claude-3-5-sonnet",
                inputTokens = 100,
                outputTokens = 50,
                cacheReadTokens = 10,
                cacheCreationTokens = 0,
                latencyMs = 250L,
                success = true,
            )
        )

        val timer = meterRegistry.get("ai.call.log.latency")
            .tag("evaluator", "BlogEvaluator")
            .tag("model", "claude-3-5-sonnet")
            .timer()

        assertThat(timer.count()).isEqualTo(1L)
    }

    @Test
    fun `success=false 기록도 예외 없이 처리되고 태그에 반영된다`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = AiCallLogObservabilityAdapter(meterRegistry)

        adapter.record(
            AiCallLog(
                evaluatorName = "FailedEvaluator",
                modelName = "claude-3-5-sonnet",
                inputTokens = 0,
                outputTokens = 0,
                cacheReadTokens = 0,
                cacheCreationTokens = 0,
                latencyMs = 5L,
                success = false,
            )
        )

        val counter = meterRegistry.get("ai.call.log.recorded")
            .tag("evaluator", "FailedEvaluator")
            .tag("model", "claude-3-5-sonnet")
            .tag("success", "false")
            .counter()

        assertThat(counter.count()).isEqualTo(1.0)
    }
}
