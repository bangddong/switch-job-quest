package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
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
class TechBlogEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1)
    private val evaluator = TechBlogEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().user(any<String>()).call().entity(AiEvaluationResult::class.java))
            .thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                techTopic = "JVM",
                title = "JVM 메모리 구조 이해하기",
                content = "JVM은 힙과 스택으로 구성됩니다"
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환한다`() {
        val expected = AiEvaluationResult(
            score = 82,
            passed = true,
            grade = "A"
        )
        whenever(chatClient.prompt().user(any<String>()).call().entity(AiEvaluationResult::class.java))
            .thenReturn(expected)

        val result = evaluator.evaluate(
            techTopic = "JVM",
            title = "JVM 메모리 구조 이해하기",
            content = "JVM은 힙과 스택으로 구성됩니다"
        )

        assertThat(result.score).isEqualTo(82)
        assertThat(result.passed).isTrue()
        assertThat(result.grade).isEqualTo("A")
    }
}
