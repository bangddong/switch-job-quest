package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface BlogEvaluatorPort : AiEvaluatorPort {
    fun evaluate(techTopic: String, title: String, content: String): AiEvaluationResult
}
