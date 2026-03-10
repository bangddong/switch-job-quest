package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.AiEvaluationResult

interface SystemDesignEvaluatorPort {
    fun evaluate(problemStatement: String, architectureDescription: String, considerations: List<String>): AiEvaluationResult
}
