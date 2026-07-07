package com.devquest.core.domain.model

import java.time.LocalDateTime

data class UserResume(
    val id: Long = 0L,
    val userId: String = "",
    val content: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
