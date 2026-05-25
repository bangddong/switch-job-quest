package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.support.AiEvaluationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient

@ExtendWith(MockitoExtension::class)
class CodingHintEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1)
    private val evaluator = CodingHintEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call()
                .entity(CodingHint::class.java)
        ).thenReturn(null)

        assertThatThrownBy { evaluator.getHint(1L, "두 수의 합", "두 정수를 더하세요", 1) }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 힌트를 그대로 반환`() {
        val expected = CodingHint(hint = "두 값을 더하는 방향으로 생각해보세요.")
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call()
                .entity(CodingHint::class.java)
        ).thenReturn(expected)

        val result = evaluator.getHint(1L, "두 수의 합", "두 정수를 더하세요", 1)

        assertThat(result.hint).isEqualTo("두 값을 더하는 방향으로 생각해보세요.")
    }

    @Test
    fun `hintLevel 3으로 호출해도 정상 동작`() {
        val expected = CodingHint(hint = "반복문을 사용하여 누적합을 구하는 의사코드를 떠올려보세요.")
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call()
                .entity(CodingHint::class.java)
        ).thenReturn(expected)

        val result = evaluator.getHint(1L, "배열 합계", "배열의 합을 구하세요", 3)

        assertThat(result.hint).isNotBlank()
    }
}
