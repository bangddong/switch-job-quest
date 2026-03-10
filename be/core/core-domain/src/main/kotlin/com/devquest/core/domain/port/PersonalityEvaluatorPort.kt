package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.AiEvaluationResult

interface PersonalityEvaluatorPort {
    fun evaluate(question: String, answer: String): AiEvaluationResult
}
