package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.ActClearReportResult
import com.devquest.core.domain.port.ActClearReportPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class ActClearReportEvaluator(
    private val chatClient: ChatClient,
    private val aiCallExecutor: AiCallExecutor
) : ActClearReportPort {

    private val template = PromptTemplate(ClassPathResource("prompts/act-clear-report.st"))

    override fun generate(actId: Int, actTitle: String, questScores: Map<String, Int>): ActClearReportResult {
        val scoresText = questScores.entries.joinToString("\n") { (questId, score) ->
            "- $questId: ${score}점"
        }
        val avgScore = if (questScores.isEmpty()) 0 else questScores.values.average().toInt()

        val prompt = template.render(mapOf(
            "actId" to actId,
            "actTitle" to actTitle,
            "scoresText" to scoresText,
            "avgScore" to avgScore,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(ActClearReportResult::class.java)
        }
    }
}
