package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.DeveloperClassResult

interface DeveloperClassEvaluatorPort {
    fun evaluate(skillAssessmentJson: String, careerEssayJson: String): DeveloperClassResult
}
