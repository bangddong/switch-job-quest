package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.CompanyFitResult

data class CompanyInfo(
    val name: String,
    val culture: String,
    val techStack: List<String>,
    val size: String,
    val description: String
)

interface CompanyFitEvaluatorPort {
    fun analyze(preferences: Map<String, String>, companies: List<CompanyInfo>): List<CompanyFitResult>
}
