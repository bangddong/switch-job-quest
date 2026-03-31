package com.devquest.core.domain.model.evaluation

data class CoachAnswerHistory(
    val question: String,
    val answer: String,
    val feedback: String,
)

data class CoachReportResult(
    val overallScore: Int = 0,
    val passLikelihood: Int = 0,
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    val finalAdvice: String = "",
)
