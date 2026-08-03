package com.devquest.core.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class DailyQuestionContent(
    val id: Long? = null,
    val questionDate: LocalDate = LocalDate.now(),
    val mailType: String = "",
    val question: String = "",
    val source: String = "",
    val category: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
