package com.devquest.core.domain

object QuestXpPolicy {

    private val baseXp: Map<String, Int> = mapOf(
        "1-1"    to 150,
        "1-2"    to 200,
        "2-1"    to 600,
        "2-2"    to 600,
        "2-3"    to 500,
        "2-BOSS" to 800,
        "3-1"    to 500,
        "3-2"    to 350,
        "4-1"    to 500,
        "4-2"    to 500,
        "1-BOSS" to 500,
        "5-1"    to 400,
        "4-BOSS" to 700,
        "5-BOSS" to 0,   // journeyReport는 XP 없음
    )

    fun calculate(questId: String, passed: Boolean, score: Int = 0, xpMultiplier: Double = 1.0): Int {
        if (!passed) return 0
        val base = baseXp[questId] ?: return 0
        return when (questId) {
            "1-2" -> base * score / 100                          // score 비례
            "2-1", "2-2", "2-3", "3-1", "5-1" -> (base * xpMultiplier).toInt()  // multiplier 적용
            else -> base                                          // 고정
        }
    }
}
