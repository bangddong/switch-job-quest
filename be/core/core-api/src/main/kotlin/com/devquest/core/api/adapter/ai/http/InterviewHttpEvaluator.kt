package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.InterviewEvaluationResult
import com.devquest.core.domain.port.InterviewEvaluatorPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/**
 * `InterviewEvaluatorPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/interview/evaluate`,
 * `POST /internal/ai/interview/questions` 호출.
 */
class InterviewHttpEvaluator(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), InterviewEvaluatorPort {

    override fun evaluate(
        category: String,
        question: String,
        answer: String,
        questionId: String,
        techStack: List<String>,
        yearsOfExperience: String,
    ): InterviewEvaluationResult =
        postJson(
            "/internal/ai/interview/evaluate",
            InterviewEvaluateHttpRequest(category, question, answer, questionId, techStack, yearsOfExperience),
        )

    override fun generateQuestions(
        techStack: List<String>,
        targetRole: String,
        yearsOfExperience: String,
        categories: List<String>,
        personalityCount: Int,
        techCount: Int,
    ): List<Map<String, String>> =
        postJson(
            "/internal/ai/interview/questions",
            InterviewGenerateQuestionsHttpRequest(
                techStack,
                targetRole,
                yearsOfExperience,
                categories,
                personalityCount,
                techCount,
            ),
        )
}

private data class InterviewEvaluateHttpRequest(
    val category: String,
    val question: String,
    val answer: String,
    val questionId: String,
    val techStack: List<String>,
    val yearsOfExperience: String,
)

private data class InterviewGenerateQuestionsHttpRequest(
    val techStack: List<String>,
    val targetRole: String,
    val yearsOfExperience: String,
    val categories: List<String>,
    val personalityCount: Int,
    val techCount: Int,
)
