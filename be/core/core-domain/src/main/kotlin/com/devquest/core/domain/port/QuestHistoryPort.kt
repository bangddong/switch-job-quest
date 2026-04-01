package com.devquest.core.domain.port

import com.devquest.core.domain.model.QuestHistory

interface QuestHistoryPort {
    fun save(history: QuestHistory): QuestHistory
    fun findAllByUserId(userId: String): List<QuestHistory>
    fun findByUserIdAndQuestId(userId: String, questId: String): List<QuestHistory>
}
