package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.EssayCheckResult
import com.devquest.core.domain.port.EssayEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class CareerEssayEvaluator(
    private val chatClient: ChatClient,
    private val aiCallExecutor: AiCallExecutor
) : EssayEvaluatorPort {

    private val template = PromptTemplate(ClassPathResource("prompts/career-essay.st"))

    override fun evaluate(dissatisfactions: List<String>, goals: List<String>, fiveYearVision: String): EssayCheckResult {
        val prompt = template.render(mapOf(
            "dissatisfactions" to dissatisfactions.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
            "goals" to goals.mapIndexed { i, g -> "${i + 1}. $g" }.joinToString("\n"),
            "fiveYearVision" to fiveYearVision,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(EssayCheckResult::class.java)
        }
    }
}
