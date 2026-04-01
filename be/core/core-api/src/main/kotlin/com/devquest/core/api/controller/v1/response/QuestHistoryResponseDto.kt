package com.devquest.core.api.controller.v1.response

import com.devquest.core.domain.model.QuestHistory
import java.time.LocalDateTime

data class QuestHistoryResponseDto(
    val id: Long?,
    val questId: String,
    val actId: Int,
    val score: Int,
    val grade: String,
    val passed: Boolean,
    val earnedXp: Int,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(history: QuestHistory): QuestHistoryResponseDto {
            return QuestHistoryResponseDto(
                id = history.id,
                questId = history.questId,
                actId = history.actId,
                score = history.score,
                grade = history.grade,
                passed = history.passed,
                earnedXp = history.earnedXp,
                createdAt = history.createdAt
            )
        }
    }
}
