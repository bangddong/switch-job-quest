package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
import com.devquest.core.domain.port.InterviewCoachPort
import tools.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/**
 * `InterviewCoachPort`의 HTTP 어댑터 — ai-api `POST /internal/ai/interview-coach/{start,answer,report}` 호출.
 */
class InterviewCoachHttpAdapter(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), InterviewCoachPort {

    override fun startSession(jdText: String, targetRole: String): CoachSessionResult =
        postJson("/internal/ai/interview-coach/start", InterviewCoachStartHttpRequest(jdText, targetRole))

    override fun evaluateAnswer(
        question: String,
        answer: String,
        questionIndex: Int,
        totalQuestions: Int,
    ): CoachAnswerResult =
        postJson(
            "/internal/ai/interview-coach/answer",
            InterviewCoachAnswerHttpRequest(question, answer, questionIndex, totalQuestions),
        )

    override fun generateReport(
        targetRole: String,
        jdSummary: String,
        answers: List<CoachAnswerHistory>,
    ): CoachReportResult =
        postJson(
            "/internal/ai/interview-coach/report",
            InterviewCoachReportHttpRequest(targetRole, jdSummary, answers),
        )
}

private data class InterviewCoachStartHttpRequest(
    val jdText: String,
    val targetRole: String,
)

private data class InterviewCoachAnswerHttpRequest(
    val question: String,
    val answer: String,
    val questionIndex: Int,
    val totalQuestions: Int,
)

private data class InterviewCoachReportHttpRequest(
    val targetRole: String,
    val jdSummary: String,
    val answers: List<CoachAnswerHistory>,
)
