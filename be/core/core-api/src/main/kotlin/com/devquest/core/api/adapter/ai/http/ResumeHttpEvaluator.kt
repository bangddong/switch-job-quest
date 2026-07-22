package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.port.ResumeEvaluatorPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `ResumeEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/resume/evaluate` 호출. */
class ResumeHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), ResumeEvaluatorPort {

    override fun evaluate(targetCompany: String, targetJd: String, resumeContent: String): ResumeCheckResult =
        postJson(
            "/internal/ai/resume/evaluate",
            ResumeEvaluateHttpRequest(targetCompany, targetJd, resumeContent),
        )
}

private data class ResumeEvaluateHttpRequest(
    val targetCompany: String,
    val targetJd: String,
    val resumeContent: String,
)
