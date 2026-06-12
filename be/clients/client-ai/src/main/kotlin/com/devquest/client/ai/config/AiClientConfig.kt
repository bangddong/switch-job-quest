package com.devquest.client.ai.config

import com.anthropic.models.messages.Model
import org.springframework.ai.anthropic.AnthropicCacheOptions
import org.springframework.ai.anthropic.AnthropicCacheStrategy
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Value
import com.devquest.client.ai.support.CacheMetricsAdvisor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class AiClientConfig {

    @Bean
    @Primary
    fun chatClient(chatModel: ChatModel, cacheMetricsAdvisor: CacheMetricsAdvisor): ChatClient {
        return ChatClient.builder(chatModel)
            .defaultAdvisors(cacheMetricsAdvisor)
            .build()
    }

    @Bean("bossChatClient")
    fun bossChatClient(
        chatModel: ChatModel,
        @Value("\${devquest.ai.boss-model:claude-sonnet-4-6}") bossModel: String,
        @Value("\${devquest.ai.boss-max-tokens:4000}") bossMaxTokens: Int,
        cacheMetricsAdvisor: CacheMetricsAdvisor,
    ): ChatClient {
        val cacheOptions = AnthropicCacheOptions.builder()
            .strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
            .build()
        val options = AnthropicChatOptions.builder()
            .model(Model.of(bossModel))
            .maxTokens(bossMaxTokens)
            .temperature(0.3)
            .cacheOptions(cacheOptions)
            .build()
        return ChatClient.builder(chatModel)
            .defaultAdvisors(cacheMetricsAdvisor)
            .defaultOptions(options.mutate())
            .build()
    }
}
