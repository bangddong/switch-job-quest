package com.devquest.client.ai.evaluator

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
class PersonalityInterviewEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val evaluator = PersonalityInterviewEvaluator(chatClient)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().user(any<String>()).call().entity(AiEvaluationResult::class.java))
            .thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                question = "갈등 상황을 어떻게 해결했나요?",
                answer = "팀원과 대화로 해결했습니다"
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("인성 면접 평가 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환한다`() {
        val expected = AiEvaluationResult(
            score = 75,
            passed = true,
            grade = "B"
        )
        whenever(chatClient.prompt().user(any<String>()).call().entity(AiEvaluationResult::class.java))
            .thenReturn(expected)

        val result = evaluator.evaluate(
            question = "갈등 상황을 어떻게 해결했나요?",
            answer = "팀원과 대화로 해결했습니다"
        )

        assertThat(result.score).isEqualTo(75)
        assertThat(result.passed).isTrue()
        assertThat(result.grade).isEqualTo("B")
    }
}
