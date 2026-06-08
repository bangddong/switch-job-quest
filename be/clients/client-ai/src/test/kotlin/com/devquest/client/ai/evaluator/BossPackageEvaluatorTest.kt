package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.AiMetricsRecorder
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.devquest.core.domain.model.evaluation.BossPackageResult
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
class BossPackageEvaluatorTest {

    private val chatClient: org.springframework.ai.chat.client.ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val metricsRecorder = AiMetricsRecorder(SimpleMeterRegistry())
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1, metricsRecorder = metricsRecorder)
    private val evaluator = BossPackageEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().entity(BossPackageResult::class.java)
        ).thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                resumeContent = "백엔드 개발 5년 경력",
                githubUrl = "https://github.com/testuser",
                blogUrl = "https://blog.example.com",
                targetPosition = "시니어 백엔드 개발자"
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("최종 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val expected = BossPackageResult(
            overallScore = 85,
            passed = true,
            resumeImpactScore = 80,
            githubConsistencyScore = 90,
            technicalDepthScore = 85,
            positionFitScore = 88,
            differentiationScore = 82,
            strengths = listOf("GitHub 활동 이력 풍부", "기술 깊이 우수"),
            improvements = listOf("블로그 콘텐츠 보강 필요"),
            overallFeedback = "전반적으로 경쟁력 있는 포트폴리오입니다"
        )
        whenever(
            chatClient.prompt().system(any<String>()).user(any<String>()).call().entity(BossPackageResult::class.java)
        ).thenReturn(expected)

        val result = evaluator.evaluate(
            resumeContent = "백엔드 개발 5년 경력",
            githubUrl = "https://github.com/testuser",
            blogUrl = "https://blog.example.com",
            targetPosition = "시니어 백엔드 개발자"
        )

        assertThat(result.overallScore).isEqualTo(85)
        assertThat(result.passed).isTrue()
        assertThat(result.resumeImpactScore).isEqualTo(80)
        assertThat(result.githubConsistencyScore).isEqualTo(90)
    }

    @Test
    fun `사용자 프롬프트에 목표 포지션, GitHub URL, 이력서 내용이 포함된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(
            chatClient.prompt().system(any<String>()).user(capture(promptCaptor)).call().entity(BossPackageResult::class.java)
        ).thenReturn(
            BossPackageResult(overallScore = 75, passed = true)
        )

        evaluator.evaluate(
            resumeContent = "Java Spring Boot 개발 경력",
            githubUrl = "https://github.com/testuser",
            blogUrl = "https://blog.example.com",
            targetPosition = "시니어 백엔드 개발자"
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains("시니어 백엔드 개발자")
        assertThat(prompt).contains("https://github.com/testuser")
        assertThat(prompt).contains("Java Spring Boot 개발 경력")
    }

    @Test
    fun `블로그 URL이 빈 문자열이면 미제공으로 대체하여 호출된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(
            chatClient.prompt().system(any<String>()).user(capture(promptCaptor)).call().entity(BossPackageResult::class.java)
        ).thenReturn(
            BossPackageResult(overallScore = 70, passed = true)
        )

        evaluator.evaluate(
            resumeContent = "백엔드 개발 경력",
            githubUrl = "https://github.com/testuser",
            blogUrl = "",
            targetPosition = "백엔드 개발자"
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains("미제공")
    }
}
