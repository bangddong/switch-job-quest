package com.devquest.core.domain.port

import com.devquest.core.domain.model.coding.CodingProblem

interface CodingProblemPort {
    fun save(problem: CodingProblem): CodingProblem
    fun findById(id: Long): CodingProblem?
    fun findByDifficultyAndLanguage(difficulty: String, language: String): List<CodingProblem>
    fun findByCategoryAndLanguage(category: String, language: String): List<CodingProblem>
}
