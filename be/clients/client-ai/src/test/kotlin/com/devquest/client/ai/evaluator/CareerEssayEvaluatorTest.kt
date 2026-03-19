package com.devquest.client.ai.evaluator

import com.devquest.core.domain.model.evaluation.EssayCheckResult
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
class CareerEssayEvaluatorTest {

    // ChatClient의 fluent API 체인 전체를 deep stub으로 목킹
    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val evaluator = CareerEssayEvaluator(chatClient)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(EssayCheckResult::class.java)
        ).thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                dissatisfactions = listOf("불만1", "불만2", "불만3"),
                goals = listOf("목표1", "목표2", "목표3"),
                fiveYearVision = "5년 후 비전"
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("파싱 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val expected = EssayCheckResult(
            score = 82,
            passed = true,
            grade = "A",
            clarityScore = 25,
            logicScore = 25,
            motivationScore = 17,
            growthScore = 15,
            feedback = "우수합니다",
            developerType = "아키텍트 지향형",
            suggestedFocus = listOf("기술 스타트업")
        )
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(EssayCheckResult::class.java)
        ).thenReturn(expected)

        val result = evaluator.evaluate(
            dissatisfactions = listOf("불만1", "불만2", "불만3"),
            goals = listOf("목표1", "목표2", "목표3"),
            fiveYearVision = "5년 후 비전"
        )

        assertThat(result.score).isEqualTo(82)
        assertThat(result.passed).isTrue()
        assertThat(result.grade).isEqualTo("A")
    }
}
