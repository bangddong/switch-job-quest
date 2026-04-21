package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
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
) : BaseAiEvaluator(chatClient, aiCallExecutor), DeveloperClassEvaluatorPort {

    private val template = PromptTemplate(ClassPathResource("prompts/developer-class.st"))

    override fun evaluate(skillAssessmentJson: String, careerEssayJson: String): DeveloperClassResult {
        val prompt = template.render(mapOf(
            "skillAssessmentJson" to skillAssessmentJson.ifBlank { "{}" },
            "careerEssayJson" to careerEssayJson.ifBlank { "{}" },
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(DeveloperClassResult::class.java)
        }
    }
}
