package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.coding.CodingProblemGenerationResult
import com.devquest.core.domain.port.CodingProblemGeneratorPort
import tools.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `CodingProblemGeneratorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/coding-problem/generate` 호출. */
class CodingProblemGeneratorHttpAdapter(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), CodingProblemGeneratorPort {

    override fun generate(difficulty: String, language: String, category: String): CodingProblemGenerationResult =
        postJson(
            "/internal/ai/coding-problem/generate",
            CodingProblemGenerateHttpRequest(difficulty, language, category),
        )
}

private data class CodingProblemGenerateHttpRequest(
    val difficulty: String,
    val language: String,
    val category: String,
)
