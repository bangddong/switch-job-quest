package com.devquest.client.ai.judge0

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Judge0ResponseTest {

    @Test
    fun `toDomain - stdout이 expectedOutput과 일치하고 Accepted이면 passed=true`() {
        val response = Judge0Response(
            stdout = "25\n",
            stderr = null,
            status = Judge0StatusResponse("Accepted")
        )

        val result = response.toDomain("25")

        assertThat(result.passed).isTrue()
        assertThat(result.status).isEqualTo("Accepted")
        assertThat(result.stdout).isEqualTo("25")
    }

    @Test
    fun `toDomain - stdout이 expectedOutput과 다르면 passed=false, status=Wrong Answer`() {
        val response = Judge0Response(
            stdout = "wrong\n",
            stderr = null,
            status = Judge0StatusResponse("Accepted")
        )

        val result = response.toDomain("25")

        assertThat(result.passed).isFalse()
        assertThat(result.status).isEqualTo("Wrong Answer")
    }

    @Test
    fun `toDomain - Judge0 status가 Accepted가 아니면 passed=false, status 유지`() {
        val response = Judge0Response(
            stdout = null,
            stderr = "Compilation error",
            status = Judge0StatusResponse("Compilation Error")
        )

        val result = response.toDomain("25")

        assertThat(result.passed).isFalse()
        assertThat(result.status).isEqualTo("Compilation Error")
    }

    @Test
    fun `toDomain - 빈 stdout과 비어있지 않은 expectedOutput이면 passed=false`() {
        val response = Judge0Response(
            stdout = "",
            stderr = null,
            status = Judge0StatusResponse("Accepted")
        )

        val result = response.toDomain("25")

        assertThat(result.passed).isFalse()
        assertThat(result.status).isEqualTo("Wrong Answer")
    }
}
