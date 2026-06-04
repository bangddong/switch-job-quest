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
        val startMs = System.currentTimeMillis()
        val response = chain.nextCall(request)
        val latencyMs = System.currentTimeMillis() - startMs

        runCatching {
            val nativeUsage = response.chatResponse()?.metadata?.usage?.nativeUsage
            val usage = nativeUsage as? Usage ?: return@runCatching

            val cacheRead = usage.cacheReadInputTokens().orElse(0L)
            val cacheCreation = usage.cacheCreationInputTokens().orElse(0L)
            val inputTokens = usage.inputTokens()
            val outputTokens = usage.outputTokens()
            val evaluatorName = AiCallContext.get()
            val modelName = response.chatResponse()?.metadata?.model ?: "unknown"

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

            meterRegistry.counter("ai.call.total", "evaluator", evaluatorName, "model", modelName).increment()
            meterRegistry.timer("ai.call.duration", "evaluator", evaluatorName, "model", modelName).record(latencyMs, TimeUnit.MILLISECONDS)
            meterRegistry.counter("ai.tokens.input", "evaluator", evaluatorName, "model", modelName).increment(inputTokens.toDouble())
            meterRegistry.counter("ai.tokens.output", "evaluator", evaluatorName, "model", modelName).increment(outputTokens.toDouble())

        }.onFailure { e ->
            log.debug("Failed to extract cache metrics", e)
        }

        return response
    }
}
