package com.devquest.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GradePolicyTest {

    @Test
    fun `일반 점수 등급 변환`() {
        assertThat(GradePolicy.from(90)).isEqualTo("S")
        assertThat(GradePolicy.from(85)).isEqualTo("A")
        assertThat(GradePolicy.from(75)).isEqualTo("B")
        assertThat(GradePolicy.from(65)).isEqualTo("C")
        assertThat(GradePolicy.from(50)).isEqualTo("D")
    }

    @Test
    fun `경계값 등급 변환`() {
        assertThat(GradePolicy.from(90)).isEqualTo("S")
        assertThat(GradePolicy.from(89)).isEqualTo("A")
        assertThat(GradePolicy.from(80)).isEqualTo("A")
        assertThat(GradePolicy.from(79)).isEqualTo("B")
        assertThat(GradePolicy.from(70)).isEqualTo("B")
        assertThat(GradePolicy.from(69)).isEqualTo("C")
        assertThat(GradePolicy.from(60)).isEqualTo("C")
        assertThat(GradePolicy.from(59)).isEqualTo("D")
    }
}
