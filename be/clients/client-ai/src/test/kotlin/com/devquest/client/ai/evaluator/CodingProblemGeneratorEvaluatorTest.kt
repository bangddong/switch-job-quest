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
class CodingProblemGeneratorEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val metricsRecorder = AiMetricsRecorder(SimpleMeterRegistry())
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1, metricsRecorder = metricsRecorder)
    private val evaluator = CodingProblemGeneratorEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(null)

        assertThatThrownBy { evaluator.generate("EASY", "JAVA", "ARRAY") }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val json = """{"title":"제곱 계산","description":"정수를 입력받아 제곱을 출력하세요.","solutionCode":"class Main {}","testCases":[{"input":"5","expectedOutput":"25"},{"input":"3","expectedOutput":"9"}]}"""
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().content()
        ).thenReturn(json)

        val result = evaluator.generate("EASY", "JAVA", "ARRAY")

        assertThat(result.title).isEqualTo("제곱 계산")
        assertThat(result.testCases).hasSize(2)
    }
}
