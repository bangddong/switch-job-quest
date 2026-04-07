package com.devquest.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class QuestXpPolicyTest {

    @Test
    fun `passed=false이면 questId에 관계없이 0 반환`() {
        assertThat(QuestXpPolicy.calculate("1-2", false, score = 80)).isEqualTo(0)
        assertThat(QuestXpPolicy.calculate("2-1", false, xpMultiplier = 1.5)).isEqualTo(0)
        assertThat(QuestXpPolicy.calculate("1-BOSS", false)).isEqualTo(0)
    }

    // ── 1-2: score 비례 ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "1-2 score={0} → XP={1}")
    @CsvSource(
        "100, 200",   // 200 * 100 / 100
        "80,  160",   // 200 * 80  / 100
        "70,  140",   // 200 * 70  / 100
        "50,  100",   // 200 * 50  / 100
        "0,   0",     // score 0 → XP 0
    )
    fun `1-2 score 비례 XP 계산`(score: Int, expectedXp: Int) {
        assertThat(QuestXpPolicy.calculate("1-2", true, score = score)).isEqualTo(expectedXp)
    }

    // ── multiplier 그룹: 2-1, 2-3, 3-1, 5-1 ─────────────────────────────────

    @ParameterizedTest(name = "{0} xpMultiplier={1} → XP={2}")
    @CsvSource(
        "2-1, 1.0, 600",   // (600 * 1.0).toInt()
        "2-1, 1.5, 900",   // (600 * 1.5).toInt()
        "2-3, 1.0, 500",   // (500 * 1.0).toInt()
        "2-3, 0.8, 400",   // (500 * 0.8).toInt()
        "3-1, 1.0, 500",   // (500 * 1.0).toInt()
        "3-1, 1.2, 600",   // (500 * 1.2).toInt()
        "5-1, 1.0, 400",   // (400 * 1.0).toInt()
        "5-1, 1.2, 480",   // (400 * 1.2).toInt()
    )
    fun `multiplier 그룹 XP 계산`(questId: String, xpMultiplier: Double, expectedXp: Int) {
        assertThat(QuestXpPolicy.calculate(questId, true, xpMultiplier = xpMultiplier)).isEqualTo(expectedXp)
    }

    // ── 고정 XP 퀘스트 ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → 고정 XP {1}")
    @CsvSource(
        "1-1,    150",
        "2-2,    800",
        "2-BOSS, 800",
        "3-2,    350",
        "4-1,    500",
        "4-2,    500",
        "1-BOSS, 500",
        "4-BOSS, 700",
        "5-BOSS, 0",    // journeyReport는 XP 없음
    )
    fun `고정 XP 퀘스트는 score와 multiplier 무관하게 고정값 반환`(questId: String, expectedXp: Int) {
        assertThat(QuestXpPolicy.calculate(questId, true, score = 99, xpMultiplier = 2.0)).isEqualTo(expectedXp)
    }

    // ── 예외 ──────────────────────────────────────────────────────────────────

    @Test
    fun `미등록 questId이면 IllegalArgumentException 발생`() {
        assertThatThrownBy { QuestXpPolicy.calculate("unknown", true) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("unknown")
    }
}
