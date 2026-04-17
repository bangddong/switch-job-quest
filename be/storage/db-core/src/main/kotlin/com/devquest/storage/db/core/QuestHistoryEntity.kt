package com.devquest.storage.db.core

import jakarta.persistence.*

@Entity
@Table(name = "quest_history")
class QuestHistoryEntity(
    @Column(nullable = false)
    val userId: String = "",

    @Column(nullable = false)
    val questId: String = "",

    @Column(nullable = false)
    val actId: Int = 0,

    @Column(nullable = false)
    val score: Int = 0,

    @Column(nullable = false, length = 10)
    val grade: String = "D",

    @Column(nullable = false)
    val passed: Boolean = false,

    @Column(nullable = false)
    val earnedXp: Int = 0
) : BaseEntity()
