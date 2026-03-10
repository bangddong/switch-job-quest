package com.devquest.core.domain.model.evaluation

data class JdAnalysisResult(
    val companyName: String = "",
    val requiredSkills: List<SkillRequirement> = emptyList(),
    val hiddenRequirements: List<String> = emptyList(),
    val overallMatchScore: Int = 0,
    val keyDifferentiators: List<String> = emptyList(),
    val applicationStrategy: String = ""
)

data class SkillRequirement(
    val skill: String = "",
    val required: Boolean = false,
    val userLevel: String = "",
    val importance: String = ""
)
