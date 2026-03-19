package com.devquest.core.domain.model

import com.devquest.core.enums.QuestStatus

data class ProgressResult(
    val userId: String,
    val totalXp: Int,
    val level: Int,
    val completedQuests: List<String>,
    val questDetails: Map<String, QuestDetail>
) {
    data class QuestDetail(
        val status: QuestStatus,
        val score: Int,
        val xp: Int
    )
}
