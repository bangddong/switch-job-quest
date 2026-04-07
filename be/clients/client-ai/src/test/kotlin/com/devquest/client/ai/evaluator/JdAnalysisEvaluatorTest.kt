package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.JdAnalysisResult
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
class JdAnalysisEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1)
    private val evaluator = JdAnalysisEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().user(any<String>()).call().entity(JdAnalysisResult::class.java))
            .thenReturn(null)

        assertThatThrownBy {
            evaluator.analyze(
                companyName = "토스",
                jobDescription = "백엔드 개발자",
                userSkills = listOf("Kotlin", "Spring"),
                userExperiences = listOf("3년 경력")
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환한다`() {
        val expected = JdAnalysisResult(
            companyName = "토스",
            overallMatchScore = 85,
            applicationStrategy = "포트폴리오 강조"
        )
        whenever(chatClient.prompt().user(any<String>()).call().entity(JdAnalysisResult::class.java))
            .thenReturn(expected)

        val result = evaluator.analyze(
            companyName = "토스",
            jobDescription = "백엔드 개발자",
            userSkills = listOf("Kotlin"),
            userExperiences = listOf("3년 경력")
        )

        assertThat(result.companyName).isEqualTo("토스")
        assertThat(result.overallMatchScore).isEqualTo(85)
    }
}
