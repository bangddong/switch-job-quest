package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.SkillAssessmentResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface SkillAssessmentPort : AiEvaluatorPort {
    fun evaluate(skills: List<String>, targetRole: String): SkillAssessmentResult
}
