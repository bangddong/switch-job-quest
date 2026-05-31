package com.devquest.core.domain.model.coding

data class CodingRankResult(
    val totalScore: Int = 0,
    val tier: String = "아이언",
    val nextTier: String? = "브론즈",
    val nextTierScore: Int? = 100,
    val easyCount: Int = 0,
    val mediumCount: Int = 0,
    val hardCount: Int = 0,
    val currentStreak: Int = 0,
    val categoryStats: Map<String, Int> = emptyMap()
)
