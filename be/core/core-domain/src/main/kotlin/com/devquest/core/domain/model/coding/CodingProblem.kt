package com.devquest.core.domain.model.coding

data class CodingProblem(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val difficulty: String = "",
    val language: String = "",
    val category: String = "",
    val solutionCode: String = "",
    val testCases: List<TestCase> = emptyList()
)
