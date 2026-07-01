package com.devquest.core.domain.model

import java.time.LocalDateTime

data class TechQuestionBank(
    val id: Long? = null,
    val category: String = "",
    val question: String = "",
    val referenceUrl: String? = null,
    val source: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
