package com.devquest.core.domain.model

import java.time.LocalDateTime

data class QuestHistory(
    val id: Long? = null,
    val userId: String,
    val questId: String,
    val actId: Int,
    val score: Int = 0,
    val grade: String = "D",
    val passed: Boolean = false,
    val earnedXp: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
