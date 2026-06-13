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
class JourneyReportGeneratorTest {

    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val metricsRecorder = AiMetricsRecorder(SimpleMeterRegistry())
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1, metricsRecorder = metricsRecorder)
    private val generator = JourneyReportGenerator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException이 발생한다`() {
        whenever(chatClient.prompt().system(any<String>()).user(any<String>()).call().content())
            .thenReturn(null)

        assertThatThrownBy {
            generator.generate(
                companyName = "토스",
                targetPosition = "시니어 백엔드 개발자",
                questScores = mapOf("1-1" to 80),
                totalXp = 1200,
                completedQuestCount = 5
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환한다`() {
        val json = """{"companyName":"토스","targetPosition":"시니어 백엔드 개발자","totalXp":1200,"completedQuestCount":5,"narrative":"훌륭한 여정이었습니다.","lowestQuestId":"1-2","highestQuestId":"2-1","finalMessage":"합격을 응원합니다!"}"""
        whenever(chatClient.prompt().system(any<String>()).user(any<String>()).call().content())
            .thenReturn(json)

        val result = generator.generate(
            companyName = "토스",
            targetPosition = "시니어 백엔드 개발자",
            questScores = mapOf("1-1" to 80, "1-2" to 60, "2-1" to 95),
            totalXp = 1200,
            completedQuestCount = 5
        )

        assertThat(result.companyName).isEqualTo("토스")
        assertThat(result.totalXp).isEqualTo(1200)
        assertThat(result.finalMessage).isEqualTo("합격을 응원합니다!")
    }

    @Test
    fun `사용자 프롬프트에 회사명과 목표 포지션이 포함된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(chatClient.prompt().system(any<String>()).user(capture(promptCaptor)).call().content())
            .thenReturn("""{"companyName":"카카오"}""")

        generator.generate(
            companyName = "카카오",
            targetPosition = "백엔드 개발자",
            questScores = mapOf("1-1" to 80),
            totalXp = 800,
            completedQuestCount = 3
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains("카카오")
        assertThat(prompt).contains("백엔드 개발자")
    }
}
