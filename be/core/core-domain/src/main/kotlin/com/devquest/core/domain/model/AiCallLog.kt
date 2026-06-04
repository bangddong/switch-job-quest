package com.devquest.core.domain.model

data class AiCallLog(
    val evaluatorName: String,
    val modelName: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheReadTokens: Int,
    val cacheCreationTokens: Int,
    val latencyMs: Long,
    val success: Boolean,
)
