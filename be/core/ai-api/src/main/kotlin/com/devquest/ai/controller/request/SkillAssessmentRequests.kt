package com.devquest.ai.controller.request

data class SkillAssessmentEvaluateRequest(
    val skills: List<String>,
    val targetRole: String,
)
