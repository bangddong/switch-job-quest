package com.devquest.core.domain

import com.devquest.core.domain.event.QuestEvaluatedEvent
import com.devquest.core.domain.model.QuestHistory
import com.devquest.core.domain.model.QuestProgress
import com.devquest.core.domain.port.QuestHistoryPort
import com.devquest.core.domain.port.QuestProgressPort
import com.devquest.core.enums.QuestStatus
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class QuestProgressRecorder(
    private val progressPort: QuestProgressPort,
    private val historyPort: QuestHistoryPort,
    private val publisher: ApplicationEventPublisher,
) {
    @Transactional
    fun record(userId: String, questId: String, actId: Int, score: Int, passed: Boolean, xp: Int, evaluationJson: String? = null) {
        val existing = progressPort.findByUserIdAndQuestId(userId, questId)
        val progress = QuestProgress(
            id = existing?.id,
            userId = userId,
            questId = questId,
            actId = actId,
            status = if (passed) QuestStatus.COMPLETED else QuestStatus.AI_FAILED,
            aiScore = score,
            earnedXp = xp,
            aiEvaluationJson = evaluationJson ?: existing?.aiEvaluationJson,
            completedAt = if (passed) LocalDateTime.now() else null,
            updatedAt = LocalDateTime.now()
        )
        progressPort.save(progress)

        val grade = GradePolicy.from(score)
        val history = QuestHistory(
            userId = userId,
            questId = questId,
            actId = actId,
            score = score,
            grade = grade,
            passed = passed,
            earnedXp = xp
        )
        historyPort.save(history)
        publisher.publishEvent(
            QuestEvaluatedEvent(
                userId = userId,
                questId = questId,
                actId = actId,
                score = score,
                grade = grade,
                passed = passed,
                earnedXp = xp,
            )
        )
    }
}
