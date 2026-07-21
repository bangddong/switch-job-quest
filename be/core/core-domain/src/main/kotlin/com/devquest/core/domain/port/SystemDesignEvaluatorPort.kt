package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface SystemDesignEvaluatorPort : AiEvaluatorPort {
    fun evaluate(problemStatement: String, architectureDescription: String, considerations: List<String>): AiEvaluationResult
}
