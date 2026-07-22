package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.EssayCheckResult
import com.devquest.core.domain.port.EssayEvaluatorPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `EssayEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/essay/evaluate` 호출. */
class EssayHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), EssayEvaluatorPort {

    override fun evaluate(
        dissatisfactions: List<String>,
        goals: List<String>,
        fiveYearVision: String,
    ): EssayCheckResult =
        postJson(
            "/internal/ai/essay/evaluate",
            EssayEvaluateHttpRequest(dissatisfactions, goals, fiveYearVision),
        )
}

private data class EssayEvaluateHttpRequest(
    val dissatisfactions: List<String>,
    val goals: List<String>,
    val fiveYearVision: String,
)
