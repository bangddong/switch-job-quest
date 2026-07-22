package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.SkillAssessmentResult
import com.devquest.core.domain.port.SkillAssessmentPort
import tools.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/** `SkillAssessmentPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/skill-assessment/evaluate` 호출. */
class SkillAssessmentHttpAdapter(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), SkillAssessmentPort {

    override fun evaluate(skills: List<String>, targetRole: String): SkillAssessmentResult =
        postJson("/internal/ai/skill-assessment/evaluate", SkillAssessmentEvaluateHttpRequest(skills, targetRole))
}

private data class SkillAssessmentEvaluateHttpRequest(
    val skills: List<String>,
    val targetRole: String,
)
