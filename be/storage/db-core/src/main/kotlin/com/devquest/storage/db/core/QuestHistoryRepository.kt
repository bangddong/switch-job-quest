package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface QuestHistoryRepository : JpaRepository<QuestHistoryEntity, Long> {
    fun findAllByUserId(userId: String): List<QuestHistoryEntity>
    fun findAllByUserIdAndQuestId(userId: String, questId: String): List<QuestHistoryEntity>
}
