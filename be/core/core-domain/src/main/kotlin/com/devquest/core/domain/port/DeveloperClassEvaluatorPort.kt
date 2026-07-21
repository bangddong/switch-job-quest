package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.DeveloperClassResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface DeveloperClassEvaluatorPort : AiEvaluatorPort {
    fun evaluate(skillAssessmentJson: String, careerEssayJson: String): DeveloperClassResult
}
