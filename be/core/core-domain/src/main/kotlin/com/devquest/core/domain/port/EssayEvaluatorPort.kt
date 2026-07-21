package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.EssayCheckResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface EssayEvaluatorPort : AiEvaluatorPort {
    fun evaluate(dissatisfactions: List<String>, goals: List<String>, fiveYearVision: String): EssayCheckResult
}
