package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.ResumeCheckResult
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
class ResumeCheckEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1)
    private val evaluator = ResumeCheckEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().user(any<String>()).call().entity(ResumeCheckResult::class.java))
            .thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                targetCompany = "토스",
                targetJd = "백엔드 개발자 채용",
                resumeContent = "3년 경력의 백엔드 개발자입니다"
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환한다`() {
        val expected = ResumeCheckResult(
            overallScore = 72,
            starMethodScore = 28,
            quantificationScore = 22,
            keywordMatchScore = 22
        )
        whenever(chatClient.prompt().user(any<String>()).call().entity(ResumeCheckResult::class.java))
            .thenReturn(expected)

        val result = evaluator.evaluate(
            targetCompany = "토스",
            targetJd = "백엔드 개발자 채용",
            resumeContent = "3년 경력의 백엔드 개발자입니다"
        )

        assertThat(result.overallScore).isEqualTo(72)
        assertThat(result.starMethodScore).isEqualTo(28)
        assertThat(result.keywordMatchScore).isEqualTo(22)
    }
}
