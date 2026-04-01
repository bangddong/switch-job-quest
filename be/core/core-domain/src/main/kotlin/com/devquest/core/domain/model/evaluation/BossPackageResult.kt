package com.devquest.core.domain.model.evaluation

data class BossPackageResult(
    val overallScore: Int = 0,
    val resumeImpactScore: Int = 0,
    val githubConsistencyScore: Int = 0,
    val technicalDepthScore: Int = 0,
    val positionFitScore: Int = 0,
    val differentiationScore: Int = 0,
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val overallFeedback: String = ""
)
