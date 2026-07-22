package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.PersonalityEvaluatorPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `PersonalityEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/personality/evaluate` 호출. */
class PersonalityHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), PersonalityEvaluatorPort {

    override fun evaluate(question: String, answer: String): AiEvaluationResult =
        postJson("/internal/ai/personality/evaluate", PersonalityEvaluateHttpRequest(question, answer))
}

private data class PersonalityEvaluateHttpRequest(
    val question: String,
    val answer: String,
)
