package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.ActClearReportResult
import com.devquest.core.domain.port.ActClearReportPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class ActClearReportEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), ActClearReportPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/act-clear-report-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/act-clear-report-user.st"))

    override fun generate(actId: Int, actTitle: String, questScores: Map<String, Int>): ActClearReportResult {
        val scoresText = questScores.entries.joinToString("\n") { (questId, score) ->
            "- $questId: ${score}점"
        }
        val avgScore = if (questScores.isEmpty()) 0 else questScores.values.average().toInt()

        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "actId" to actId,
            "actTitle" to actTitle,
            "scoresText" to scoresText,
            "avgScore" to avgScore,
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName) {
            chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(ActClearReportResult::class.java)
        }
    }
}
