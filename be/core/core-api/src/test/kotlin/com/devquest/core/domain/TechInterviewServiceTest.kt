package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class TechInterviewServiceTest {

    @Mock lateinit var techInterviewPort: TechInterviewPort
    @Mock lateinit var questProgressRecorder: QuestProgressRecorder

    @InjectMocks
    private lateinit var service: TechInterviewService

    @Test
    fun `generateQuestion - 포트에 위임하여 질문 목록을 반환한다`() {
        val expected = TechInterviewResult(questions = listOf("질문1", "질문2"))
        whenever(techInterviewPort.generateQuestions("Java")).thenReturn(expected)

        val result = service.generateQuestion("user1", "Java")

        assertThat(result.questions).containsExactly("질문1", "질문2")
    }

    @Test
    fun `evaluate - 점수 70 이상이면 passed=true로 progress 저장`() {
        val result = TechInterviewResult(questions = listOf("Q1"), overallScore = 80, passed = true)
        whenever(techInterviewPort.evaluate(any(), any(), any())).thenReturn(result)

        val returned = service.evaluate("user1", "Java", listOf("Q1"), listOf("A1"))

        assertThat(returned.passed).isTrue()
        verify(questProgressRecorder).record(
            eq("user1"), eq(QuestConstants.TECH_INTERVIEW), eq(1), eq(80), eq(true), any(), isNull()
        )
    }

    @Test
    fun `evaluate - 점수 70 미만이면 passed=false로 progress 저장`() {
        val result = TechInterviewResult(questions = listOf("Q1"), overallScore = 50, passed = false)
        whenever(techInterviewPort.evaluate(any(), any(), any())).thenReturn(result)

        val returned = service.evaluate("user1", "Kotlin", listOf("Q1"), listOf("A1"))

        assertThat(returned.passed).isFalse()
        verify(questProgressRecorder).record(
            eq("user1"), eq(QuestConstants.TECH_INTERVIEW), eq(1), eq(50), eq(false), any(), isNull()
        )
    }

    @Test
    fun `evaluate - userId가 null이면 questProgressRecorder를 호출하지 않는다`() {
        val result = TechInterviewResult(questions = listOf("Q1"), overallScore = 80, passed = true)
        whenever(techInterviewPort.evaluate(any(), any(), any())).thenReturn(result)

        val returned = service.evaluate(null, "Java", listOf("Q1"), listOf("A1"))

        assertThat(returned.passed).isTrue()
        verify(questProgressRecorder, never()).record(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `evaluate - AI 평가가 실패하면 questProgressRecorder를 호출하지 않고 예외가 그대로 전파된다 (트랜잭션 재배치 회귀 가드)`() {
        whenever(techInterviewPort.evaluate(any(), any(), any())).thenThrow(RuntimeException("AI 호출 실패"))

        assertThat(
            org.assertj.core.api.Assertions.catchThrowable {
                service.evaluate("user1", "Java", listOf("Q1"), listOf("A1"))
            }
        ).isInstanceOf(RuntimeException::class.java).hasMessage("AI 호출 실패")

        verify(questProgressRecorder, never()).record(any(), any(), any(), any(), any(), any(), any())
    }
}
