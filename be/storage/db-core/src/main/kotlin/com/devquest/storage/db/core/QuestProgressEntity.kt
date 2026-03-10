package com.devquest.storage.db.core

import com.devquest.core.enums.QuestStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "quest_progress")
class QuestProgressEntity(
    @Column(nullable = false)
    val userId: String = "",

    @Column(nullable = false)
    val questId: String = "",

    @Column(nullable = false)
    val actId: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: QuestStatus = QuestStatus.NOT_STARTED,

    var aiScore: Int = 0,
    var earnedXp: Int = 0,

    @Column(columnDefinition = "TEXT")
    var aiEvaluationJson: String? = null,

    var completedAt: LocalDateTime? = null
) : BaseEntity()
