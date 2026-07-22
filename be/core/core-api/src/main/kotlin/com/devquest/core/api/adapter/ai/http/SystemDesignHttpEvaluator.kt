package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.SystemDesignEvaluatorPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `SystemDesignEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/system-design/evaluate` 호출. */
class SystemDesignHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), SystemDesignEvaluatorPort {

    override fun evaluate(
        problemStatement: String,
        architectureDescription: String,
        considerations: List<String>,
    ): AiEvaluationResult =
        postJson(
            "/internal/ai/system-design/evaluate",
            SystemDesignEvaluateHttpRequest(problemStatement, architectureDescription, considerations),
        )
}

private data class SystemDesignEvaluateHttpRequest(
    val problemStatement: String,
    val architectureDescription: String,
    val considerations: List<String>,
)
