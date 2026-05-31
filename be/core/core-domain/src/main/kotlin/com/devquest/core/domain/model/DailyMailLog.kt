package com.devquest.core.domain.model

import java.time.LocalDateTime

data class DailyMailLog(
    val id: Long = 0L,
    val userId: String = "",
    val mailType: String = "",
    val questionContent: String = "",
    val sentAt: LocalDateTime,
)
