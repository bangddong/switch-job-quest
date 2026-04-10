package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.JourneyReportResult
import com.devquest.core.domain.port.JourneyReportPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class JourneyReportGenerator(
    @Qualifier("bossChatClient") chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), JourneyReportPort {

    private val template = PromptTemplate(ClassPathResource("prompts/journey-report.st"))

    override fun generate(
        companyName: String,
        targetPosition: String,
        questScores: Map<String, Int>,
        totalXp: Int,
        completedQuestCount: Int,
    ): JourneyReportResult {
        val scoresText = questScores.entries.joinToString("\n") { (questId, score) ->
            "- $questId: ${score}점"
        }
        val lowestEntry = questScores.minByOrNull { it.value }
        val highestEntry = questScores.maxByOrNull { it.value }

        val prompt = template.render(mapOf(
            "companyName" to companyName,
            "targetPosition" to targetPosition,
            "completedQuestCount" to completedQuestCount,
            "totalXp" to totalXp,
            "scoresText" to scoresText,
            "lowestQuestId" to (lowestEntry?.key ?: ""),
            "highestQuestId" to (highestEntry?.key ?: ""),
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(JourneyReportResult::class.java)
        }
    }
}
