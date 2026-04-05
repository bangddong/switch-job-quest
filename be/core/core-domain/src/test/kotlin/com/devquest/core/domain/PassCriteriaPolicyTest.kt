package com.devquest.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PassCriteriaPolicyTest {

    @Test
    fun `evaluate - score 70 이상이면 true`() {
        assertThat(PassCriteriaPolicy.evaluate(70)).isTrue()
        assertThat(PassCriteriaPolicy.evaluate(85)).isTrue()
        assertThat(PassCriteriaPolicy.evaluate(100)).isTrue()
    }

    @Test
    fun `evaluate - score 70 미만이면 false`() {
        assertThat(PassCriteriaPolicy.evaluate(69)).isFalse()
        assertThat(PassCriteriaPolicy.evaluate(0)).isFalse()
    }

    @Test
    fun `evaluate - 커스텀 passScore 적용`() {
        assertThat(PassCriteriaPolicy.evaluate(80, passScore = 80)).isTrue()
        assertThat(PassCriteriaPolicy.evaluate(79, passScore = 80)).isFalse()
    }

    @Test
    fun `evaluateMax - 빈 리스트는 false`() {
        assertThat(PassCriteriaPolicy.evaluateMax(emptyList())).isFalse()
    }

    @Test
    fun `evaluateMax - 최대값이 70 이상이면 true`() {
        assertThat(PassCriteriaPolicy.evaluateMax(listOf(50, 70, 60))).isTrue()
        assertThat(PassCriteriaPolicy.evaluateMax(listOf(90))).isTrue()
    }

    @Test
    fun `evaluateMax - 최대값이 70 미만이면 false`() {
        assertThat(PassCriteriaPolicy.evaluateMax(listOf(50, 60, 65))).isFalse()
    }
}
