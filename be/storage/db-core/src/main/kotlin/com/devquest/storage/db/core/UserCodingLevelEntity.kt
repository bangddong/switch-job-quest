package com.devquest.storage.db.core

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "user_coding_level")
class UserCodingLevelEntity(
    @Id
    @Column(nullable = false)
    val userId: String = "",

    @Column(nullable = false)
    var level: Int = 1,

    @Column(nullable = false)
    var solveCount: Int = 0,

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
