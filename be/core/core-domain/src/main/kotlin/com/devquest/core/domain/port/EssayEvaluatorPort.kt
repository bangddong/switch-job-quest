package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.EssayCheckResult

interface EssayEvaluatorPort {
    fun evaluate(dissatisfactions: List<String>, goals: List<String>, fiveYearVision: String): EssayCheckResult
}
