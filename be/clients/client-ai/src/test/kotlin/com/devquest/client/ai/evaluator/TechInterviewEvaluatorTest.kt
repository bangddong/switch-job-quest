package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.AiMetricsRecorder
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
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
class TechInterviewEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val metricsRecorder = AiMetricsRecorder(SimpleMeterRegistry())
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1, metricsRecorder = metricsRecorder)
    private val evaluator = TechInterviewEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `generateDailyQuestion — AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(null)

        assertThatThrownBy { evaluator.generateDailyQuestion("Java,Spring Boot,JPA") }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `generateDailyQuestion — AI가 정상 응답을 반환하면 질문 텍스트를 그대로 반환`() {
        val expected = "JPA에서 N+1 문제란 무엇이며 어떻게 해결하나요?"
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(expected)

        val result = evaluator.generateDailyQuestion("Java,Spring Boot,JPA")

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `generateQuestions — AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(null)

        assertThatThrownBy { evaluator.generateQuestions("Java,Spring Boot") }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `generateQuestions — AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val json = """{"questions":["Q1","Q2"],"overallScore":0,"feedback":"","passed":false,"modelAnswer":""}"""
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(json)

        val result = evaluator.generateQuestions("Java,Spring Boot")

        assertThat(result.questions).hasSize(2)
    }
}
