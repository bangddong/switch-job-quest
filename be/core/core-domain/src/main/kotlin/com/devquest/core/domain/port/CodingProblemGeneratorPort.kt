package com.devquest.core.domain.port

import com.devquest.core.domain.model.coding.CodingProblemGenerationResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface CodingProblemGeneratorPort : AiEvaluatorPort {
    fun generate(difficulty: String, language: String, category: String): CodingProblemGenerationResult
}
