package com.devquest.client.ai.support

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class AiMetricsRecorder(private val meterRegistry: MeterRegistry) {

    fun recordCallSuccess(evaluatorType: String, model: String) {
        meterRegistry.counter(
            "devquest.ai.call.total",
            "evaluatorType", evaluatorType,
            "model", model,
            "status", "success"
        ).increment()
    }

    fun recordCallFailure(evaluatorType: String, model: String) {
        meterRegistry.counter(
            "devquest.ai.call.total",
            "evaluatorType", evaluatorType,
            "model", model,
            "status", "failure"
        ).increment()
    }

    fun recordRetry(evaluatorType: String, model: String) {
        meterRegistry.counter(
            "devquest.ai.retry.total",
            "evaluatorType", evaluatorType,
            "model", model
        ).increment()
    }
}
