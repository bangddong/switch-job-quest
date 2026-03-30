package com.devquest.client.ai.evaluator

import com.devquest.core.domain.model.evaluation.SkillAssessmentResult
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
class SkillAssessmentEvaluatorTest {

    private val chatClient: org.springframework.ai.chat.client.ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val evaluator = SkillAssessmentEvaluator(chatClient)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException 발생`() {
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(SkillAssessmentResult::class.java)
        ).thenReturn(null)

        assertThatThrownBy {
            evaluator.evaluate(
                skills = listOf("Java:5년", "Spring Boot:3년"),
                targetRole = "시니어 백엔드 개발자"
            )
        }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("기술 스택 진단 실패")
    }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환`() {
        val expected = SkillAssessmentResult(
            score = 78,
            passed = true,
            grade = "B",
            developerType = "안정형 백엔드 스페셜리스트",
            strengths = listOf("Java 장기 실무 경험", "Spring Boot 숙련도"),
            improvements = listOf("Kubernetes 경험 부족", "클라우드 네이티브 전환 필요", "컨테이너 오케스트레이션 학습 우선"),
            feedback = "백엔드 핵심 기술은 탄탄하나 클라우드 역량 보완 필요"
        )
        whenever(
            chatClient.prompt().user(any<String>()).call().entity(SkillAssessmentResult::class.java)
        ).thenReturn(expected)

        val result = evaluator.evaluate(
            skills = listOf("Java:5년", "Spring Boot:3년", "Kubernetes:6개월"),
            targetRole = "시니어 백엔드 개발자"
        )

        assertThat(result.score).isEqualTo(78)
        assertThat(result.passed).isTrue()
        assertThat(result.grade).isEqualTo("B")
        assertThat(result.developerType).isEqualTo("안정형 백엔드 스페셜리스트")
    }

    @Test
    fun `프롬프트에 경력기간 형식 안내와 기술 목록이 포함된다`() {
        val promptCaptor = ArgumentCaptor.forClass(String::class.java)
        whenever(
            chatClient.prompt().user(capture(promptCaptor)).call().entity(SkillAssessmentResult::class.java)
        ).thenReturn(
            SkillAssessmentResult(score = 70, passed = true, grade = "B")
        )

        evaluator.evaluate(
            skills = listOf("Java:5년", "Spring Boot:3년"),
            targetRole = "시니어 백엔드 개발자"
        )

        val prompt = promptCaptor.value
        assertThat(prompt).contains("기술명:경력기간")
        assertThat(prompt).contains("Java:5년")
        assertThat(prompt).contains("Spring Boot:3년")
        assertThat(prompt).contains("시니어 백엔드 개발자")
        assertThat(prompt).contains("경력기간을 고려한")
    }
}
