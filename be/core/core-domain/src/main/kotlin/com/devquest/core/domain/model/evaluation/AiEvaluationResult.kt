package com.devquest.core.domain.model.evaluation

data class AiEvaluationResult(
    val score: Int = 0,
    val passed: Boolean = false,
    val grade: String = "D",
    val summary: String = "",
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val detailedFeedback: String = "",
    val xpMultiplier: Double = 1.0,
    val retryAllowed: Boolean = true
)
