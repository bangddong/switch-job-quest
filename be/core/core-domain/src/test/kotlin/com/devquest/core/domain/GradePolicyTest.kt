package com.devquest.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GradePolicyTest {

    @ParameterizedTest(name = "score={0} → {1}")
    @CsvSource(
        "100, S",
        "90,  S",
        "89,  A",
        "80,  A",
        "79,  B",
        "70,  B",
        "69,  C",
        "60,  C",
        "59,  D",
        "1,   D",
        "0,   D",
    )
    fun `경계값 포함 전 구간 등급 변환`(score: Int, expected: String) {
        assertThat(GradePolicy.from(score)).isEqualTo(expected)
    }
}
