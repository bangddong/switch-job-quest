package com.devquest.core.domain.model

import com.devquest.core.enums.QuestStatus
import java.time.LocalDateTime

data class QuestProgress(
    val id: Long? = null,
    val userId: String,
    val questId: String,
    val actId: Int,
    val status: QuestStatus = QuestStatus.NOT_STARTED,
    val aiScore: Int = 0,
    val earnedXp: Int = 0,
    val aiEvaluationJson: String? = null,
    val completedAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
