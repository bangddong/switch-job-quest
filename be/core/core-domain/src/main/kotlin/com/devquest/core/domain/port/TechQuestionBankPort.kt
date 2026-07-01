package com.devquest.core.domain.port

import com.devquest.core.domain.model.TechQuestionBank

interface TechQuestionBankPort {
    fun findUnused(excludeQuestions: List<String>, category: String? = null): TechQuestionBank?
}
