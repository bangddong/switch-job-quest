package com.devquest.core.domain.model.evaluation

data class SkillAssessmentResult(
    val score: Int = 0,
    val passed: Boolean = true,
    val grade: String = "B",
    val developerType: String = "",
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val feedback: String = "",
)
