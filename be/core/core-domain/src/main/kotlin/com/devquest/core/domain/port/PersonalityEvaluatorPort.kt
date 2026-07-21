package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface PersonalityEvaluatorPort : AiEvaluatorPort {
    fun evaluate(question: String, answer: String): AiEvaluationResult
}
