package com.devquest.core.domain.model.evaluation

data class ResumeCheckResult(
    val overallScore: Int = 0,
    val starMethodScore: Int = 0,
    val quantificationScore: Int = 0,
    val keywordMatchScore: Int = 0,
    val improvements: List<ResumeImprovement> = emptyList(),
    val rewrittenExamples: List<ResumeRewrite> = emptyList()
)

data class ResumeImprovement(
    val section: String = "",
    val original: String = "",
    val issue: String = "",
    val suggestion: String = ""
)

data class ResumeRewrite(
    val original: String = "",
    val improved: String = "",
    val explanation: String = ""
)
