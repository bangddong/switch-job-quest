package com.devquest.core.domain

import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.DailyQuestionContentPort
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class DailyQuestionServiceTest {

    @Mock lateinit var dailyQuestionContentPort: DailyQuestionContentPort
    @Mock lateinit var techInterviewPort: TechInterviewPort
    @Mock lateinit var dailyQuestionContentService: DailyQuestionContentService

    @InjectMocks
    private lateinit var service: DailyQuestionService

    @Test
    fun `getTodayQuestion - 오늘 질문이 존재하면 반환한다`() {
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any()))
            .thenReturn(DailyQuestionContent(question = "Java의 GC 동작 방식을 설명하세요.", mailType = "TECH_INTERVIEW"))

        val result = service.getTodayQuestion()

        assertThat(result).isEqualTo("Java의 GC 동작 방식을 설명하세요.")
    }

    @Test
    fun `getTodayQuestion - 오늘 행이 있으면 뱅크 lazy 생성 로직을 호출하지 않는다 (멱등)`() {
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any()))
            .thenReturn(DailyQuestionContent(question = "Java의 GC 동작 방식을 설명하세요.", mailType = "TECH_INTERVIEW"))

        service.getTodayQuestion()

        verify(dailyQuestionContentService, never()).ensureTodayQuestionFromBank()
    }

    @Test
    fun `getTodayQuestion - 오늘 행이 없어도 뱅크에 후보가 있으면 뱅크에서 생성해 반환한다 (00시~09시 공백 해소)`() {
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentService.ensureTodayQuestionFromBank())
            .thenReturn(DailyQuestionContent(question = "뱅크에서 생성된 질문", mailType = "TECH_INTERVIEW", source = "BANK"))

        val result = service.getTodayQuestion()

        assertThat(result).isEqualTo("뱅크에서 생성된 질문")
        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
    }

    @Test
    fun `getTodayQuestion - 오늘 행이 없고 뱅크도 소진이면 CoreException(DAILY_QUESTION_NOT_FOUND)을 던진다`() {
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentService.ensureTodayQuestionFromBank()).thenReturn(null)

        assertThatThrownBy { service.getTodayQuestion() }
            .isInstanceOf(CoreException::class.java)
            .extracting { (it as CoreException).errorType }
            .isEqualTo(ErrorType.DAILY_QUESTION_NOT_FOUND)

        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
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

    @Test
    fun `explain - TechInterviewPort에 올바른 파라미터로 위임한다`() {
        val question = "OSIV란 무엇인가요?"
        val answer = "영속성 컨텍스트를 뷰까지 열어두는 전략입니다."
        val feedback = "핵심은 맞지만 트랜잭션 범위 설명이 부족합니다."
        val userQuestion = "트랜잭션 범위가 정확히 뭔가요?"
        val expected = "트랜잭션 범위는 @Transactional 메서드의 시작과 끝 구간을 의미합니다."
        whenever(
            techInterviewPort.explainFollowup(
                eq(question), eq(answer), eq(feedback), eq(userQuestion), eq(null),
            )
        ).thenReturn(expected)

        val result = service.explain(question, answer, feedback, userQuestion, null)

        assertThat(result).isEqualTo(expected)
        verify(techInterviewPort).explainFollowup(question, answer, feedback, userQuestion, null)
    }
}
