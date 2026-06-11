package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.DailyMailLogPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class DailyQuestionServiceTest {

    @Mock lateinit var dailyMailLogPort: DailyMailLogPort
    @Mock lateinit var techInterviewPort: TechInterviewPort

    @InjectMocks
    private lateinit var service: DailyQuestionService

    @Test
    fun `getTodayQuestion - 오늘 질문이 존재하면 반환한다`() {
        whenever(dailyMailLogPort.findTodayQuestion(eq("TECH_INTERVIEW"), any()))
            .thenReturn("Java의 GC 동작 방식을 설명하세요.")

        val result = service.getTodayQuestion()

        assertThat(result).isEqualTo("Java의 GC 동작 방식을 설명하세요.")
    }

    @Test
    fun `getTodayQuestion - 오늘 질문이 없으면 CoreException(DAILY_QUESTION_NOT_FOUND)을 던진다`() {
        whenever(dailyMailLogPort.findTodayQuestion(eq("TECH_INTERVIEW"), any()))
            .thenReturn(null)

        assertThatThrownBy { service.getTodayQuestion() }
            .isInstanceOf(CoreException::class.java)
            .extracting { (it as CoreException).errorType }
            .isEqualTo(ErrorType.DAILY_QUESTION_NOT_FOUND)
    }

    @Test
    fun `evaluate - TechInterviewPort에 올바른 파라미터로 위임한다`() {
        val question = "Java의 GC 동작 방식을 설명하세요."
        val answer = "GC는 힙 메모리에서 사용하지 않는 객체를 자동으로 제거합니다."
        val expected = TechInterviewResult(overallScore = 85, passed = true)
        whenever(techInterviewPort.evaluate(eq("Java,Spring Boot,JPA"), eq(listOf(question)), eq(listOf(answer))))
            .thenReturn(expected)

        val result = service.evaluate(question, answer)

        assertThat(result.overallScore).isEqualTo(85)
        verify(techInterviewPort).evaluate("Java,Spring Boot,JPA", listOf(question), listOf(answer))
    }
}
