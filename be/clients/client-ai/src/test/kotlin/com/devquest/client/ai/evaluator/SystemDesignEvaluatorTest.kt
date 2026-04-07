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
class SystemDesignEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1)
    private val evaluator = SystemDesignEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().user(any<String>()).call().entity(AiEvaluationResult::class.java))
            .thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                problemStatement = "URL 단축 서비스를 설계하세요",
                architectureDescription = "Redis와 MySQL을 사용합니다",
                considerations = listOf("확장성", "가용성")
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환한다`() {
        val expected = AiEvaluationResult(
            score = 80,
            passed = true,
            grade = "A"
        )
        whenever(chatClient.prompt().user(any<String>()).call().entity(AiEvaluationResult::class.java))
            .thenReturn(expected)

        val result = evaluator.evaluate(
            problemStatement = "URL 단축 서비스를 설계하세요",
            architectureDescription = "Redis와 MySQL을 사용합니다",
            considerations = listOf("확장성", "가용성")
        )

        assertThat(result.score).isEqualTo(80)
        assertThat(result.passed).isTrue()
        assertThat(result.grade).isEqualTo("A")
    }
}
