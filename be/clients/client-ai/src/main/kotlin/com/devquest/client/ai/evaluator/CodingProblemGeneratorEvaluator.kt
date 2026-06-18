package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.coding.CodingProblemGenerationResult
import com.devquest.core.domain.port.CodingProblemGeneratorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class CodingProblemGeneratorEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), CodingProblemGeneratorPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/coding-problem-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/coding-problem-user.st"))

    override fun generate(difficulty: String, language: String, category: String): CodingProblemGenerationResult {
        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "difficulty" to difficulty,
            "language" to language,
            "category" to category
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, CodingProblemGenerationResult::class.java)
        }
    }
}
