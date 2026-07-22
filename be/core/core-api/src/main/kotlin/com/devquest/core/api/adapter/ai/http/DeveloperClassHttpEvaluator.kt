package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.DeveloperClassResult
import com.devquest.core.domain.port.DeveloperClassEvaluatorPort
import tools.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `DeveloperClassEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/developer-class/evaluate` 호출. */
class DeveloperClassHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), DeveloperClassEvaluatorPort {

    override fun evaluate(skillAssessmentJson: String, careerEssayJson: String): DeveloperClassResult =
        postJson(
            "/internal/ai/developer-class/evaluate",
            DeveloperClassEvaluateHttpRequest(skillAssessmentJson, careerEssayJson),
        )
}

private data class DeveloperClassEvaluateHttpRequest(
    val skillAssessmentJson: String,
    val careerEssayJson: String,
)
