package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.BossPackageResult
import com.devquest.core.domain.port.BossPackageEvaluatorPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `BossPackageEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/boss-package/evaluate` 호출. */
class BossPackageHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), BossPackageEvaluatorPort {

    override fun evaluate(
        resumeContent: String,
        githubUrl: String,
        blogUrl: String,
        targetPosition: String,
    ): BossPackageResult =
        postJson(
            "/internal/ai/boss-package/evaluate",
            BossPackageEvaluateHttpRequest(resumeContent, githubUrl, blogUrl, targetPosition),
        )
}

private data class BossPackageEvaluateHttpRequest(
    val resumeContent: String,
    val githubUrl: String,
    val blogUrl: String,
    val targetPosition: String,
)
