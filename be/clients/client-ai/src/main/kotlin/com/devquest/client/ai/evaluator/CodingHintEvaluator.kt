package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.port.CodingHintPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class CodingHintEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), CodingHintPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/coding-hint-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/coding-hint-user.st"))

    override fun getHint(problemId: Long, title: String, description: String, hintLevel: Int): CodingHint {
        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "problemId" to problemId,
            "title" to title,
            "description" to description,
            "hintLevel" to hintLevel
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName) {
            chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(CodingHint::class.java)
        }
    }
}
