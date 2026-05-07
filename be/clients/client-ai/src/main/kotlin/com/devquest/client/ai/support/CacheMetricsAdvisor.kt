package com.devquest.client.ai.support

import com.anthropic.models.messages.Usage
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.core.Ordered
import org.springframework.stereotype.Component

@Component
class CacheMetricsAdvisor : CallAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName(): String = "CacheMetricsAdvisor"

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun adviseCall(request: ChatClientRequest, chain: CallAdvisorChain): ChatClientResponse {
        val response = chain.nextCall(request)

        runCatching {
            val nativeUsage = response.chatResponse()?.metadata?.usage?.nativeUsage
            val usage = nativeUsage as? Usage ?: return@runCatching

            val cacheRead = usage.cacheReadInputTokens().orElse(0L)
            val cacheCreation = usage.cacheCreationInputTokens().orElse(0L)
            val inputTokens = usage.inputTokens()
            val outputTokens = usage.outputTokens()

            log.info(
                "AI cache metrics — cacheHit={}, cache_read_input_tokens={}, cache_creation_input_tokens={}, input_tokens={}, output_tokens={}",
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
        }.onFailure { e ->
            log.debug("Failed to extract cache metrics", e)
        }

        return response
    }
}
