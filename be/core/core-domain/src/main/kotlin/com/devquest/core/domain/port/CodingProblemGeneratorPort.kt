package com.devquest.core.domain.port

import com.devquest.core.domain.model.coding.CodingProblemGenerationResult

interface CodingProblemGeneratorPort {
    fun generate(difficulty: String, language: String): CodingProblemGenerationResult
}
