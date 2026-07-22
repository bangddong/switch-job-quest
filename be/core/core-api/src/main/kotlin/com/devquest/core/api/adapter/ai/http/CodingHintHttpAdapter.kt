package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.port.CodingHintPort
import tools.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `CodingHintPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/coding-hint/get` 호출. */
class CodingHintHttpAdapter(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), CodingHintPort {

    override fun getHint(problemId: Long, title: String, description: String, hintLevel: Int): CodingHint =
        postJson(
            "/internal/ai/coding-hint/get",
            CodingHintGetHttpRequest(problemId, title, description, hintLevel),
        )
}

private data class CodingHintGetHttpRequest(
    val problemId: Long,
    val title: String,
    val description: String,
    val hintLevel: Int,
)
