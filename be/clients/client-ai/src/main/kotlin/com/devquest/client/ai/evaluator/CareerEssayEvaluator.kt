package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.EssayCheckResult
import com.devquest.core.domain.port.EssayEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class CareerEssayEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), EssayEvaluatorPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/career-essay-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/career-essay-user.st"))

    override fun evaluate(dissatisfactions: List<String>, goals: List<String>, fiveYearVision: String): EssayCheckResult {
        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "dissatisfactions" to dissatisfactions.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
            "goals" to goals.mapIndexed { i, g -> "${i + 1}. $g" }.joinToString("\n"),
            "fiveYearVision" to fiveYearVision,
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content()
            parseContent(content, EssayCheckResult::class.java)
        }
    }
}
