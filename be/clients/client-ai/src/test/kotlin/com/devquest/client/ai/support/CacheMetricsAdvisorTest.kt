package com.devquest.client.ai.support

import com.devquest.core.domain.model.AiCallLog
import com.devquest.core.domain.port.AiCallLogPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain

@ExtendWith(MockitoExtension::class)
class CacheMetricsAdvisorTest {

    @Mock
    private lateinit var aiCallLogPort: AiCallLogPort

    private val meterRegistry: MeterRegistry = SimpleMeterRegistry()

    private lateinit var advisor: CacheMetricsAdvisor

    @BeforeEach
    fun setup() {
        advisor = CacheMetricsAdvisor(aiCallLogPort, meterRegistry)
        AiCallContext.set("TestEvaluator")
    }

    @AfterEach
    fun teardown() {
        AiCallContext.clear()
    }

    @Test
    fun `AI 응답 usage가 null이어도 advisor는 정상 반환한다`() {
        val request: ChatClientRequest = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        val response: ChatClientResponse = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        val chain: CallAdvisorChain = mock()

        whenever(chain.nextCall(request)).thenReturn(response)
        whenever(response.chatResponse()?.metadata?.usage?.nativeUsage).thenReturn(null)

        val result = advisor.adviseCall(request, chain)

        assertThat(result).isEqualTo(response)
    }

    @Test
    fun `Usage 정보가 있으면 AiCallLogPort에 record가 호출된다`() {
        val request: ChatClientRequest = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        val response: ChatClientResponse = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        val chain: CallAdvisorChain = mock()
        val nativeUsage: com.anthropic.models.messages.Usage = mock(defaultAnswer = RETURNS_DEEP_STUBS)

        whenever(chain.nextCall(request)).thenReturn(response)
        whenever(response.chatResponse()?.metadata?.usage?.nativeUsage).thenReturn(nativeUsage)
        whenever(nativeUsage.inputTokens()).thenReturn(100L)
        whenever(nativeUsage.outputTokens()).thenReturn(50L)
        whenever(nativeUsage.cacheReadInputTokens()).thenReturn(java.util.Optional.of(20L))
        whenever(nativeUsage.cacheCreationInputTokens()).thenReturn(java.util.Optional.of(0L))
        whenever(response.chatResponse()?.metadata?.model).thenReturn("claude-3-5-sonnet")

        advisor.adviseCall(request, chain)

        val captor = argumentCaptor<AiCallLog>()
        verify(aiCallLogPort).record(captor.capture())
        assertThat(captor.firstValue.evaluatorName).isEqualTo("TestEvaluator")
        assertThat(captor.firstValue.inputTokens).isEqualTo(100)
        assertThat(captor.firstValue.outputTokens).isEqualTo(50)
        assertThat(captor.firstValue.cacheReadTokens).isEqualTo(20)
        assertThat(captor.firstValue.success).isTrue()
    }
}
