package com.devquest.client.ai.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class AiCallContextTest {

    @AfterEach
    fun teardown() {
        AiCallContext.clear()
    }

    @Test
    fun `set 후 get 하면 설정한 값을 반환한다`() {
        AiCallContext.set("CareerEssayEvaluator")
        assertThat(AiCallContext.get()).isEqualTo("CareerEssayEvaluator")
    }

    @Test
    fun `clear 후 get 하면 Unknown을 반환한다`() {
        AiCallContext.set("SomeEvaluator")
        AiCallContext.clear()
        assertThat(AiCallContext.get()).isEqualTo("Unknown")
    }

    @Test
    fun `set 없이 get 하면 Unknown을 반환한다`() {
        assertThat(AiCallContext.get()).isEqualTo("Unknown")
    }
}
