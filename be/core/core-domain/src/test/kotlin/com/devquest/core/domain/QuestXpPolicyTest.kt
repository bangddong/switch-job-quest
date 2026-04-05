package com.devquest.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class QuestXpPolicyTest {

    @Test
    fun `passed=false이면 questId에 관계없이 0 반환`() {
        assertThat(QuestXpPolicy.calculate("1-2", false, score = 80)).isEqualTo(0)
        assertThat(QuestXpPolicy.calculate("2-1", false, xpMultiplier = 1.5)).isEqualTo(0)
        assertThat(QuestXpPolicy.calculate("1-BOSS", false)).isEqualTo(0)
    }

    @Test
    fun `1-2는 score 비례로 계산`() {
        // baseXp["1-2"] = 200, score=80 → 200 * 80 / 100 = 160
        val result = QuestXpPolicy.calculate("1-2", true, score = 80)
        assertThat(result).isEqualTo(160)
    }

    @Test
    fun `2-1은 xpMultiplier 적용`() {
        // baseXp["2-1"] = 600, xpMultiplier=1.5 → (600 * 1.5).toInt() = 900
        val result = QuestXpPolicy.calculate("2-1", true, xpMultiplier = 1.5)
        assertThat(result).isEqualTo(900)
    }

    @Test
    fun `5-1은 xpMultiplier 적용`() {
        // baseXp["5-1"] = 400, xpMultiplier=1.2 → (400 * 1.2).toInt() = 480
        val result = QuestXpPolicy.calculate("5-1", true, xpMultiplier = 1.2)
        assertThat(result).isEqualTo(480)
    }

    @Test
    fun `1-BOSS는 고정 XP 반환`() {
        // baseXp["1-BOSS"] = 500
        val result = QuestXpPolicy.calculate("1-BOSS", true)
        assertThat(result).isEqualTo(500)
    }

    @Test
    fun `4-BOSS는 고정 XP 반환`() {
        // baseXp["4-BOSS"] = 700
        val result = QuestXpPolicy.calculate("4-BOSS", true)
        assertThat(result).isEqualTo(700)
    }

    @Test
    fun `미등록 questId이면 IllegalArgumentException 발생`() {
        assertThatThrownBy { QuestXpPolicy.calculate("unknown", true) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("unknown")
    }
}
