package com.devquest.core.domain.model.evaluation

data class CoachAnswerResult(
    val feedback: String = "",
    val score: Int = 0,
    val improvements: List<String> = emptyList(),
    val encouragement: String = "",
)
