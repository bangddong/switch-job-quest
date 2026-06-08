package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.AiMetricsRecorder
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.devquest.core.domain.model.evaluation.DeveloperClassResult
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

@ExtendWith(MockitoExtension::class)
class DeveloperClassEvaluatorTest {

    private val chatClient: org.springframework.ai.chat.client.ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val metricsRecorder = AiMetricsRecorder(SimpleMeterRegistry())
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1, metricsRecorder = metricsRecorder)
    private val evaluator = DeveloperClassEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().entity(DeveloperClassResult::class.java)
        ).thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                skillAssessmentJson = """{"score": 78}""",
                careerEssayJson = """{"score": 82}"""
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val expected = DeveloperClassResult(
            overallScore = 80,
            passed = true,
            developerClass = "시니어 백엔드 엔지니어",
            classDescription = "높은 기술 역량과 커리어 방향성이 명확한 개발자",
            strengths = listOf("Java 장기 실무 경험", "아키텍처 설계 능력"),
            strategies = listOf("클라우드 역량 강화", "리더십 경험 추가"),
            overallFeedback = "전반적으로 우수한 역량을 보유하고 있습니다"
        )
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().entity(DeveloperClassResult::class.java)
        ).thenReturn(expected)

        val result = evaluator.evaluate(
            skillAssessmentJson = """{"score": 78}""",
            careerEssayJson = """{"score": 82}"""
        )

        assertThat(result.overallScore).isEqualTo(80)
        assertThat(result.passed).isTrue()
        assertThat(result.developerClass).isEqualTo("시니어 백엔드 엔지니어")
        assertThat(result.classDescription).isEqualTo("높은 기술 역량과 커리어 방향성이 명확한 개발자")
    }

    @Test
    fun `사용자 프롬프트에 기술 평가 JSON과 커리어 에세이 JSON이 포함된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(
            chatClient.prompt().system(any<String>()).user(capture(promptCaptor)).call().entity(DeveloperClassResult::class.java)
        ).thenReturn(
            DeveloperClassResult(overallScore = 75, passed = true, developerClass = "미드레벨")
        )

        evaluator.evaluate(
            skillAssessmentJson = """{"score": 78, "grade": "B"}""",
            careerEssayJson = """{"score": 82, "grade": "A"}"""
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains(""""score": 78""")
        assertThat(prompt).contains(""""score": 82""")
    }

    @Test
    fun `빈 문자열 입력 시 기본값으로 대체하여 호출된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(
            chatClient.prompt().system(any<String>()).user(capture(promptCaptor)).call().entity(DeveloperClassResult::class.java)
        ).thenReturn(
            DeveloperClassResult(overallScore = 50, passed = false, developerClass = "주니어")
        )

        evaluator.evaluate(
            skillAssessmentJson = "",
            careerEssayJson = ""
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains("{}")
    }
}
