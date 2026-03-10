package com.devquest.core.domain

import com.devquest.core.domain.port.QuestProgressPort
import com.devquest.core.enums.QuestStatus
import org.springframework.stereotype.Service

@Service
class ProgressService(
    private val progressPort: QuestProgressPort
) {
    fun getProgress(userId: String): Map<String, Any> {
        val progresses = progressPort.findAllByUserId(userId)
        val totalXp = progresses.filter { it.status == QuestStatus.COMPLETED }.sumOf { it.earnedXp }
        val completedQuests = progresses.filter { it.status == QuestStatus.COMPLETED }.map { it.questId }

        return mapOf(
            "userId" to userId,
            "totalXp" to totalXp,
            "completedQuests" to completedQuests,
            "level" to (totalXp / 500 + 1),
            "questDetails" to progresses.associate {
                it.questId to mapOf(
                    "status" to it.status,
                    "score" to it.aiScore,
                    "xp" to it.earnedXp
                )
            }
        )
    }
}
