package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.SkillAssessmentResult

interface SkillAssessmentPort {
    fun evaluate(skills: List<String>, targetRole: String): SkillAssessmentResult
}
