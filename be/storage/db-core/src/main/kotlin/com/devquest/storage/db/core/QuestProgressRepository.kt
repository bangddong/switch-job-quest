package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface QuestProgressRepository : JpaRepository<QuestProgressEntity, Long> {
    fun findByUserIdAndQuestId(userId: String, questId: String): QuestProgressEntity?
    fun findAllByUserId(userId: String): List<QuestProgressEntity>
}
