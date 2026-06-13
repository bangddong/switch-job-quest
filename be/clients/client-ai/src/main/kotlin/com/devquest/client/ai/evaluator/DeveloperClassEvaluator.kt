package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.client.ai.support.BaseAiEvaluator.Companion.AiModel
import com.devquest.core.domain.model.evaluation.DeveloperClassResult
import com.devquest.core.domain.port.DeveloperClassEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class DeveloperClassEvaluator(
    @Qualifier("bossChatClient") chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor, AiModel.SONNET), DeveloperClassEvaluatorPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/developer-class-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/developer-class-user.st"))

    override fun evaluate(skillAssessmentJson: String, careerEssayJson: String): DeveloperClassResult {
        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "skillAssessmentJson" to skillAssessmentJson.ifBlank { "{}" },
            "careerEssayJson" to careerEssayJson.ifBlank { "{}" },
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content()
            parseContent(content, DeveloperClassResult::class.java)
        }
    }
}
