package com.devquest.core.domain.port

import com.devquest.core.domain.model.coding.CodingHint

interface CodingHintPort {
    fun getHint(problemId: Long, title: String, description: String, hintLevel: Int): CodingHint
}
