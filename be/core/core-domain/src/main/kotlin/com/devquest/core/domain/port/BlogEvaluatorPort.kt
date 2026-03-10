package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.AiEvaluationResult

interface BlogEvaluatorPort {
    fun evaluate(techTopic: String, title: String, content: String): AiEvaluationResult
}
