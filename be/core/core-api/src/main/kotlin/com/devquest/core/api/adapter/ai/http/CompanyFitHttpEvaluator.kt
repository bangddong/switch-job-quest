package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.CompanyFitResult
import com.devquest.core.domain.port.CompanyFitEvaluatorPort
import com.devquest.core.domain.port.CompanyInfo
import tools.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `CompanyFitEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/company-fit/analyze` 호출. */
class CompanyFitHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), CompanyFitEvaluatorPort {

    override fun analyze(preferences: Map<String, String>, companies: List<CompanyInfo>): List<CompanyFitResult> =
        postJson("/internal/ai/company-fit/analyze", CompanyFitAnalyzeHttpRequest(preferences, companies))
}

private data class CompanyFitAnalyzeHttpRequest(
    val preferences: Map<String, String>,
    val companies: List<CompanyInfo>,
)
