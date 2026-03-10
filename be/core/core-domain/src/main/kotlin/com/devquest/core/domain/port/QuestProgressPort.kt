package com.devquest.core.domain.port

import com.devquest.core.domain.model.QuestProgress

interface QuestProgressPort {
    fun findByUserIdAndQuestId(userId: String, questId: String): QuestProgress?
    fun findAllByUserId(userId: String): List<QuestProgress>
    fun save(progress: QuestProgress): QuestProgress
}
