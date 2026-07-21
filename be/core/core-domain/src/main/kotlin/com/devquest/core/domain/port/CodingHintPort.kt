package com.devquest.core.domain.port

import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface CodingHintPort : AiEvaluatorPort {
    fun getHint(problemId: Long, title: String, description: String, hintLevel: Int): CodingHint
}
