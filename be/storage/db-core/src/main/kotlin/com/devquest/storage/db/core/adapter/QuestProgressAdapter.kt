package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.QuestProgress
import com.devquest.core.domain.port.QuestProgressPort
import com.devquest.storage.db.core.QuestProgressEntity
import com.devquest.storage.db.core.QuestProgressRepository
import org.springframework.stereotype.Component

@Component
class QuestProgressAdapter(
    private val repository: QuestProgressRepository
) : QuestProgressPort {

    override fun findByUserIdAndQuestId(userId: String, questId: String): QuestProgress? {
        return repository.findByUserIdAndQuestId(userId, questId)?.toDomain()
    }

    override fun findAllByUserId(userId: String): List<QuestProgress> {
        return repository.findAllByUserId(userId).map { it.toDomain() }
    }

    override fun save(progress: QuestProgress): QuestProgress {
        val progressId = progress.id
        val entity = if (progressId != null) {
            repository.findById(progressId).orElse(null)?.apply {
                status = progress.status
                aiScore = progress.aiScore
                earnedXp = progress.earnedXp
                aiEvaluationJson = progress.aiEvaluationJson
                completedAt = progress.completedAt
            } ?: toEntity(progress)
        } else {
            toEntity(progress)
        }
        return repository.save(entity).toDomain()
    }

    private fun QuestProgressEntity.toDomain(): QuestProgress {
        return QuestProgress(
            id = this.id,
            userId = this.userId,
            questId = this.questId,
            actId = this.actId,
            status = this.status,
            aiScore = this.aiScore,
            earnedXp = this.earnedXp,
            aiEvaluationJson = this.aiEvaluationJson,
            completedAt = this.completedAt,
            updatedAt = this.updatedAt
        )
    }

    private fun toEntity(progress: QuestProgress): QuestProgressEntity {
        return QuestProgressEntity(
            userId = progress.userId,
            questId = progress.questId,
            actId = progress.actId,
            status = progress.status,
            aiScore = progress.aiScore,
            earnedXp = progress.earnedXp,
            aiEvaluationJson = progress.aiEvaluationJson,
            completedAt = progress.completedAt
        )
    }
}
