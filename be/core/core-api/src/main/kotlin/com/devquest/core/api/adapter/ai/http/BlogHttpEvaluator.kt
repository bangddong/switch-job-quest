package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.BlogEvaluatorPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/**
 * `BlogEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/blog/evaluate` 호출.
 *
 * Phase 1 Task 1.4a. `devquest.ai.transport=http`일 때만 [com.devquest.core.api.config.AiTransportConfig]가
 * 이 클래스를 빈으로 등록한다(기본값 `inprocess`에서는 등록조차 되지 않음).
 */
class BlogHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), BlogEvaluatorPort {

    override fun evaluate(techTopic: String, title: String, content: String): AiEvaluationResult =
        postJson("/internal/ai/blog/evaluate", BlogEvaluateHttpRequest(techTopic, title, content))
}

private data class BlogEvaluateHttpRequest(
    val techTopic: String,
    val title: String,
    val content: String,
)
