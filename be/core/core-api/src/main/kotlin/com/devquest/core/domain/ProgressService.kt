package com.devquest.core.domain

import com.devquest.core.domain.model.ProgressResult
import com.devquest.core.domain.port.QuestProgressPort
import com.devquest.core.enums.QuestStatus
import org.springframework.stereotype.Service

@Service
class ProgressService(
    private val progressPort: QuestProgressPort
) {
    fun getProgress(userId: String): ProgressResult {
        val progresses = progressPort.findAllByUserId(userId)
        val completed = progresses.filter { it.status == QuestStatus.COMPLETED }
        val totalXp = completed.sumOf { it.earnedXp }

        return ProgressResult(
            userId = userId,
            totalXp = totalXp,
            level = totalXp / 500 + 1,
            completedQuests = completed.map { it.questId },
            questDetails = progresses.associate {
                it.questId to ProgressResult.QuestDetail(
                    status = it.status,
                    score = it.aiScore,
                    xp = it.earnedXp
                )
            }
        )
    }
}
