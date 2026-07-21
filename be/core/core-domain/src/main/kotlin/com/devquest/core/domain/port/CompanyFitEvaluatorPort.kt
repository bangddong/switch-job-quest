package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.CompanyFitResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

data class CompanyInfo(
    val name: String,
    val culture: String,
    val techStack: List<String>,
    val size: String,
    val description: String
)

interface CompanyFitEvaluatorPort : AiEvaluatorPort {
    fun analyze(preferences: Map<String, String>, companies: List<CompanyInfo>): List<CompanyFitResult>
}
