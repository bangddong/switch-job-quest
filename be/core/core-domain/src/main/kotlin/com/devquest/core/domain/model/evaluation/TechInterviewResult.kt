package com.devquest.core.domain.model.evaluation

data class TechInterviewResult(
    val questions: List<String> = emptyList(),
    val overallScore: Int = 0,
    val feedback: String = "",
    val passed: Boolean = false,
    val modelAnswer: String = "",
)
