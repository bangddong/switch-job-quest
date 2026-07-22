package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.ActClearReportResult
import com.devquest.core.domain.port.ActClearReportPort
import tools.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `ActClearReportPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/act-clear-report/generate` 호출. */
class ActClearReportHttpAdapter(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), ActClearReportPort {

    override fun generate(actId: Int, actTitle: String, questScores: Map<String, Int>): ActClearReportResult =
        postJson(
            "/internal/ai/act-clear-report/generate",
            ActClearReportGenerateHttpRequest(actId, actTitle, questScores),
        )
}

private data class ActClearReportGenerateHttpRequest(
    val actId: Int,
    val actTitle: String,
    val questScores: Map<String, Int>,
)
