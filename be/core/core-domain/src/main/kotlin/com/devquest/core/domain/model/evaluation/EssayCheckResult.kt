package com.devquest.core.domain.model.evaluation

data class EssayCheckResult(
    val score: Int = 0,
    val passed: Boolean = false,
    val grade: String = "D",
    val clarityScore: Int = 0,
    val logicScore: Int = 0,
    val motivationScore: Int = 0,
    val growthScore: Int = 0,
    val feedback: String = "",
    val developerType: String = "",
    val suggestedFocus: List<String> = emptyList()
)
