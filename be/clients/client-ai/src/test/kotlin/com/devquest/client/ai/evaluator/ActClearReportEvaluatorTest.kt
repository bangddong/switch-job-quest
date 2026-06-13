package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.AiMetricsRecorder
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.devquest.core.domain.support.AiEvaluationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
class ActClearReportEvaluatorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val metricsRecorder = AiMetricsRecorder(SimpleMeterRegistry())
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1, metricsRecorder = metricsRecorder)
    private val evaluator = ActClearReportEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().system(any<String>()).user(any<String>()).call().content())
            .thenReturn(null)

        assertThatThrownBy {
            evaluator.generate(
                actId = 1,
                actTitle = "이력서 작성",
                questScores = mapOf("1-1" to 80, "1-2" to 70)
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환한다`() {
        val json = """{"actId":1,"actTitle":"이력서 작성","overallScore":75,"grade":"B","developerClass":"주니어 백엔드","achievements":["이력서 구조화 완료"],"nextActHint":"기술 스택 정리 필요","encouragement":"잘 하셨어요!"}"""
        whenever(chatClient.prompt().system(any<String>()).user(any<String>()).call().content())
            .thenReturn(json)

        val result = evaluator.generate(
            actId = 1,
            actTitle = "이력서 작성",
            questScores = mapOf("1-1" to 80, "1-2" to 70)
        )

        assertThat(result.actId).isEqualTo(1)
        assertThat(result.overallScore).isEqualTo(75)
        assertThat(result.grade).isEqualTo("B")
        assertThat(result.achievements).hasSize(1)
    }

    @Test
    fun `사용자 프롬프트에 actId와 점수 목록이 포함된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(chatClient.prompt().system(any<String>()).user(capture(promptCaptor)).call().content())
            .thenReturn("""{"actId":2,"actTitle":"기술 면접"}""")

        evaluator.generate(
            actId = 2,
            actTitle = "기술 면접",
            questScores = mapOf("2-1" to 90, "2-2" to 85)
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains("2-1")
        assertThat(prompt).contains("90")
    }
}
