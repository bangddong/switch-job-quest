package com.devquest.core.domain.model.evaluation

data class DeveloperClassResult(
    val overallScore: Int = 0,
    val passed: Boolean = false,
    val developerClass: String = "",
    val classDescription: String = "",
    val strengths: List<String> = emptyList(),
    val strategies: List<String> = emptyList(),
    val overallFeedback: String = ""
)
