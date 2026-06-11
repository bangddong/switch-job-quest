package com.devquest.client.ai.support

import com.anthropic.models.messages.Usage
import com.devquest.core.domain.model.AiCallLog
import com.devquest.core.domain.port.AiCallLogPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class CacheMetricsAdvisor(
    private val aiCallLogPort: AiCallLogPort,
    private val meterRegistry: MeterRegistry,
) : CallAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName(): String = "CacheMetricsAdvisor"

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun adviseCall(request: ChatClientRequest, chain: CallAdvisorChain): ChatClientResponse {
        log.info("CacheMetricsAdvisor: adviseCall invoked, evaluator={}", AiCallContext.get())
        val startMs = System.currentTimeMillis()
        val response = chain.nextCall(request)
        val latencyMs = System.currentTimeMillis() - startMs

        runCatching {
            val chatResponse = response.chatResponse()
            if (chatResponse == null) {
                log.warn(
                    "CacheMetricsAdvisor: chatResponse() is null — response type={}, evaluator={}",
                    response.javaClass.name,
                    AiCallContext.get(),
                )
                return@runCatching
            }
            val springUsage = chatResponse.metadata?.usage ?: run {
                log.warn("CacheMetricsAdvisor: usage is null in response metadata")
                return@runCatching
            }

            val evaluatorName = AiCallContext.get()
            val modelName = chatResponse.metadata?.model ?: "unknown"

            val nativeUsage = springUsage.nativeUsage
            val anthropicUsage = nativeUsage as? Usage

            if (anthropicUsage == null) {
                log.warn(
                    "CacheMetricsAdvisor: nativeUsage cast failed — type={}, evaluator={}. Recording basic token metrics only.",
                    nativeUsage?.javaClass?.name ?: "null",
                    evaluatorName,
                )
                // nativeUsage unavailable — record basic token metrics via Spring AI Usage interface
                val inputTokens = springUsage.promptTokens?.toLong() ?: 0L
                val outputTokens = springUsage.completionTokens?.toLong() ?: 0L
                meterRegistry.timer("ai.call.duration", "evaluatorType", evaluatorName, "model", modelName).record(latencyMs, TimeUnit.MILLISECONDS)
                meterRegistry.counter("ai.tokens.input", "evaluatorType", evaluatorName, "model", modelName).increment(inputTokens.toDouble())
                meterRegistry.counter("ai.tokens.output", "evaluatorType", evaluatorName, "model", modelName).increment(outputTokens.toDouble())
                meterRegistry.counter("ai.tokens.cache_read", "evaluatorType", evaluatorName, "model", modelName).increment(0.0)
                meterRegistry.counter("ai.tokens.cache_creation", "evaluatorType", evaluatorName, "model", modelName).increment(0.0)
                return@runCatching
            }

            val cacheRead = anthropicUsage.cacheReadInputTokens().orElse(0L)
            val cacheCreation = anthropicUsage.cacheCreationInputTokens().orElse(0L)
            val inputTokens = anthropicUsage.inputTokens()
            val outputTokens = anthropicUsage.outputTokens()

            log.info(
                "AI cache metrics — evaluator={}, model={}, latencyMs={}, cacheHit={}, cache_read_input_tokens={}, cache_creation_input_tokens={}, input_tokens={}, output_tokens={}",
                evaluatorName,
                modelName,
                latencyMs,
                cacheRead > 0,
                cacheRead,
                cacheCreation,
                inputTokens,
                outputTokens,
            )

            if (cacheCreation > 2_000 && cacheRead == 0L) {
                log.warn(
                    "AI cache miss — cache_creation_input_tokens={} with no cache read. Possible cache break or first call.",
                    cacheCreation,
                )
            }

            runCatching {
                aiCallLogPort.record(
                    AiCallLog(
                        evaluatorName = evaluatorName,
                        modelName = modelName,
                        inputTokens = inputTokens.toInt(),
                        outputTokens = outputTokens.toInt(),
                        cacheReadTokens = cacheRead.toInt(),
                        cacheCreationTokens = cacheCreation.toInt(),
                        latencyMs = latencyMs,
                        success = true,
                    )
                )
            }.onFailure { e ->
                log.warn("AI 메트릭 DB 저장 실패", e)
            }

            meterRegistry.timer("ai.call.duration", "evaluatorType", evaluatorName, "model", modelName).record(latencyMs, TimeUnit.MILLISECONDS)
            meterRegistry.counter("ai.tokens.input", "evaluatorType", evaluatorName, "model", modelName).increment(inputTokens.toDouble())
            meterRegistry.counter("ai.tokens.output", "evaluatorType", evaluatorName, "model", modelName).increment(outputTokens.toDouble())
            meterRegistry.counter("ai.tokens.cache_read", "evaluatorType", evaluatorName, "model", modelName).increment(cacheRead.toDouble())
            meterRegistry.counter("ai.tokens.cache_creation", "evaluatorType", evaluatorName, "model", modelName).increment(cacheCreation.toDouble())

        }.onFailure { e ->
            log.warn("CacheMetricsAdvisor: unexpected error extracting metrics", e)
        }

        return response
    }
}
