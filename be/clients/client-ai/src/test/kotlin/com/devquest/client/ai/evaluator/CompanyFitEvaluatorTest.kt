package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.CompanyFitResult
import com.devquest.core.domain.port.CompanyInfo
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
class CompanyFitEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1)
    private val evaluator = CompanyFitEvaluator(chatClient, aiCallExecutor)

    private val preferences = mapOf("문화" to "수평적", "기술스택" to "Kotlin")
    private val companies = listOf(
        CompanyInfo(name = "토스", culture = "수평적", techStack = listOf("Kotlin"), size = "중견", description = "핀테크")
    )

    @Test
    fun `AI가 유효한 JSON을 반환하면 파싱된 결과 목록을 반환한다`() {
        val json = """[{"companyName":"토스","fitScore":90,"fitGrade":"A","cultureFit":23,"techFit":24,"growthFit":22,"lifestyleFit":21,"pros":["수평적 문화"],"cons":[],"recommendation":"적극 추천"}]"""
        whenever(chatClient.prompt().user(any<String>()).call().content()).thenReturn(json)

        val result = evaluator.analyze(preferences, companies)

        assertThat(result).hasSize(1)
        assertThat(result[0].companyName).isEqualTo("토스")
        assertThat(result[0].fitScore).isEqualTo(90)
        assertThat(result[0].fitGrade).isEqualTo("A")
    }

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().user(any<String>()).call().content()).thenReturn(null)

        assertThatThrownBy { evaluator.analyze(preferences, companies) }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 잘못된 JSON을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().user(any<String>()).call().content()).thenReturn("invalid json")

        assertThatThrownBy { evaluator.analyze(preferences, companies) }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("파싱 실패")
    }
}
