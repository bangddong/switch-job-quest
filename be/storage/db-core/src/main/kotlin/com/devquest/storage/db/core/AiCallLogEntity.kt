package com.devquest.storage.db.core

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "ai_call_log")
class AiCallLogEntity(
    @Column(nullable = false, length = 100)
    val evaluatorName: String = "",

    @Column(nullable = false, length = 100)
    val modelName: String = "",

    @Column(nullable = false)
    val inputTokens: Int = 0,

    @Column(nullable = false)
    val outputTokens: Int = 0,

    @Column(nullable = false)
    val cacheReadTokens: Int = 0,

    @Column(nullable = false)
    val cacheCreationTokens: Int = 0,

    @Column(nullable = false)
    val latencyMs: Long = 0L,

    @Column(nullable = false)
    val success: Boolean = true,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
