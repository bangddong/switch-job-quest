package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.JourneyReportResult
import com.devquest.core.domain.port.JourneyReportPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `JourneyReportPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/journey-report/generate` 호출. */
class JourneyReportHttpAdapter(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), JourneyReportPort {

    override fun generate(
        companyName: String,
        targetPosition: String,
        questScores: Map<String, Int>,
        totalXp: Int,
        completedQuestCount: Int,
    ): JourneyReportResult =
        postJson(
            "/internal/ai/journey-report/generate",
            JourneyReportGenerateHttpRequest(companyName, targetPosition, questScores, totalXp, completedQuestCount),
        )
}

private data class JourneyReportGenerateHttpRequest(
    val companyName: String,
    val targetPosition: String,
    val questScores: Map<String, Int>,
    val totalXp: Int,
    val completedQuestCount: Int,
)
