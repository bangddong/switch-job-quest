package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.InterviewEvaluationResult
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
class MockInterviewEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1)
    private val evaluator = MockInterviewEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().user(any<String>()).call().entity(InterviewEvaluationResult::class.java))
            .thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                category = "기술",
                question = "JVM이란?",
                answer = "자바 가상 머신입니다",
                questionId = "q-1"
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환한다`() {
        val expected = InterviewEvaluationResult(
            questionId = "q-1",
            question = "JVM이란?",
            score = 80,
            passed = true
        )
        whenever(chatClient.prompt().user(any<String>()).call().entity(InterviewEvaluationResult::class.java))
            .thenReturn(expected)

        val result = evaluator.evaluate(
            category = "기술",
            question = "JVM이란?",
            answer = "자바 가상 머신입니다",
            questionId = "q-1"
        )

        assertThat(result.questionId).isEqualTo("q-1")
        assertThat(result.score).isEqualTo(80)
        assertThat(result.passed).isTrue()
    }
}
