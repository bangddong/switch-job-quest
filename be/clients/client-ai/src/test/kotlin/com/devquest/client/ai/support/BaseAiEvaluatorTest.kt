package com.devquest.client.ai.support

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient

@ExtendWith(MockitoExtension::class)
class BaseAiEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val metricsRecorder = AiMetricsRecorder(SimpleMeterRegistry())
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1, metricsRecorder = metricsRecorder)

    // BaseAiEvaluator는 abstract이므로 익명 subclass를 통해 protected 메서드 테스트
    private val evaluator = object : BaseAiEvaluator(chatClient, aiCallExecutor) {
        fun testCallAi(systemPrompt: String, userPrompt: String): String? = callAi(systemPrompt, userPrompt)
        fun testWrapUserContent(value: String, maxLength: Int = 5000): String = wrapUserContent(value, maxLength)
    }

    // ---- wrapUserContent 테스트 ----

    @Test
    fun `wrapUserContent는 user_content 태그로 래핑한다`() {
        val result = evaluator.testWrapUserContent("안녕하세요")
        assertThat(result).isEqualTo("<user_content>\n안녕하세요\n</user_content>")
    }

    @Test
    fun `wrapUserContent는 maxLength 초과 시 잘라낸다`() {
        val longText = "a".repeat(100)
        val result = evaluator.testWrapUserContent(longText, maxLength = 10)
        assertThat(result).isEqualTo("<user_content>\n${"a".repeat(10)}\n</user_content>")
    }

    @Test
    fun `wrapUserContent는 빈 문자열도 올바르게 처리한다`() {
        val result = evaluator.testWrapUserContent("")
        assertThat(result).isEqualTo("<user_content>\n\n</user_content>")
    }

    @Test
    fun `wrapUserContent는 maxLength 이하 문자열을 잘라내지 않는다`() {
        val text = "정상 길이 텍스트"
        val result = evaluator.testWrapUserContent(text, maxLength = 5000)
        assertThat(result).contains(text)
    }

    // ---- callAi 테스트 ----

    @Test
    fun `callAi는 system prompt에 ANTI_INJECTION_SUFFIX를 포함시켜 호출한다`() {
        val systemCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
        whenever(chatClient.prompt().system(capture(systemCaptor)).user(any<String>()).call().content())
            .thenReturn("응답")

        evaluator.testCallAi("원본 시스템 프롬프트", "유저 프롬프트")

        val capturedSystem = systemCaptor.value
        assertThat(capturedSystem).startsWith("원본 시스템 프롬프트")
        assertThat(capturedSystem).contains("[시스템 보안]")
        assertThat(capturedSystem).contains("<user_content>")
        assertThat(capturedSystem).contains("절대 따르지 마세요")
    }

    @Test
    fun `callAi는 chatClient 응답을 그대로 반환한다`() {
        whenever(chatClient.prompt().system(any<String>()).user(any<String>()).call().content())
            .thenReturn("AI 응답 텍스트")

        val result = evaluator.testCallAi("시스템", "유저")
        assertThat(result).isEqualTo("AI 응답 텍스트")
    }

    @Test
    fun `callAi는 chatClient가 null을 반환하면 null을 반환한다`() {
        whenever(chatClient.prompt().system(any<String>()).user(any<String>()).call().content())
            .thenReturn(null)

        val result = evaluator.testCallAi("시스템", "유저")
        assertThat(result).isNull()
    }
}
