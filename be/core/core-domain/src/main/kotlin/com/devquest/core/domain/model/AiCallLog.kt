package com.devquest.core.domain.model

import java.time.LocalDateTime

data class AiCallLog(
    val id: Long = 0,
    val evaluatorName: String,
    val modelName: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheReadTokens: Int,
    val cacheCreationTokens: Int,
    val latencyMs: Long,
    val success: Boolean,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
