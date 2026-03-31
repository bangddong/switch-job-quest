package com.devquest.core.domain.model.evaluation

data class CoachQuestion(
    val index: Int,
    val question: String,
    val competency: String,
)

data class CoachSessionResult(
    val jdSummary: String = "",
    val keyCompetencies: List<String> = emptyList(),
    val questions: List<CoachQuestion> = emptyList(),
)
