package com.devquest.core.domain.model.coding

data class CodingProblemGenerationResult(
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val solutionCode: String = "",
    val testCases: List<TestCase> = emptyList()
)
