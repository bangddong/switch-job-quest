package com.devquest.core.domain.event

data class QuestEvaluatedEvent(
    val userId: String,
    val questId: String,
    val actId: Int,
    val score: Int,
    val grade: String,
    val passed: Boolean,
    val earnedXp: Int,
)
