package com.devquest.ai.controller.request

import com.devquest.core.domain.model.evaluation.CoachAnswerHistory

data class InterviewCoachStartRequest(
    val jdText: String,
    val targetRole: String,
)

data class InterviewCoachAnswerRequest(
    val question: String,
    val answer: String,
    val questionIndex: Int,
    val totalQuestions: Int,
)

data class InterviewCoachReportRequest(
    val targetRole: String,
    val jdSummary: String,
    val answers: List<CoachAnswerHistory>,
)
