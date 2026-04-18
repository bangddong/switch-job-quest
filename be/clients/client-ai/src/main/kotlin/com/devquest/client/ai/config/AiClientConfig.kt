package com.devquest.client.ai.config

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.Model
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class AiClientConfig {

    @Bean
    @Primary
    fun chatClient(chatModel: ChatModel): ChatClient {
        return ChatClient.builder(chatModel).build()
    }

    @Bean("bossChatClient")
    fun bossChatClient(
        @Value("\${devquest.ai.boss-model:claude-sonnet-4-6}") bossModel: String,
        @Value("\${devquest.ai.boss-max-tokens:4000}") bossMaxTokens: Int,
        @Value("\${spring.ai.anthropic.api-key}") apiKey: String,
    ): ChatClient {
        val anthropicClient = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .build()
        val options = AnthropicChatOptions.builder()
            .model(Model.of(bossModel))
            .maxTokens(bossMaxTokens)
            .temperature(0.3)
            .build()
        val model = AnthropicChatModel.builder()
            .anthropicClient(anthropicClient)
            .options(options)
            .build()
        return ChatClient.builder(model).build()
    }
}
