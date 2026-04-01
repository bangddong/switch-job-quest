package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.QuestHistory
import com.devquest.core.domain.port.QuestHistoryPort
import com.devquest.storage.db.core.QuestHistoryEntity
import com.devquest.storage.db.core.QuestHistoryRepository
import org.springframework.stereotype.Component

@Component
class QuestHistoryAdapter(
    private val repository: QuestHistoryRepository
) : QuestHistoryPort {

    override fun save(history: QuestHistory): QuestHistory {
        return repository.save(toEntity(history)).toDomain()
    }

    override fun findAllByUserId(userId: String): List<QuestHistory> {
        return repository.findAllByUserId(userId).map { it.toDomain() }
    }

    override fun findByUserIdAndQuestId(userId: String, questId: String): List<QuestHistory> {
        return repository.findAllByUserIdAndQuestId(userId, questId).map { it.toDomain() }
    }

    private fun QuestHistoryEntity.toDomain(): QuestHistory {
        return QuestHistory(
            id = this.id,
            userId = this.userId,
            questId = this.questId,
            actId = this.actId,
            score = this.score,
            grade = this.grade,
            passed = this.passed,
            earnedXp = this.earnedXp,
            createdAt = this.createdAt
        )
    }

    private fun toEntity(history: QuestHistory): QuestHistoryEntity {
        return QuestHistoryEntity(
            userId = history.userId,
            questId = history.questId,
            actId = history.actId,
            score = history.score,
            grade = history.grade,
            passed = history.passed,
            earnedXp = history.earnedXp
        )
    }
}
