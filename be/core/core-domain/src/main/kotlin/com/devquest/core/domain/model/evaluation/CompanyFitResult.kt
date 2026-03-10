package com.devquest.core.domain.model.evaluation

data class CompanyFitResult(
    val companyName: String = "",
    val fitScore: Int = 0,
    val fitGrade: String = "D",
    val cultureFit: Int = 0,
    val techFit: Int = 0,
    val growthFit: Int = 0,
    val lifestyleFit: Int = 0,
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val recommendation: String = ""
)
