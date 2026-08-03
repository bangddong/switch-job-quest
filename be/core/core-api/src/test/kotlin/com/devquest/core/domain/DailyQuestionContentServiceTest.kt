package com.devquest.core.domain

import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.core.domain.model.TechQuestionBank
import com.devquest.core.domain.port.DailyQuestionContentPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.domain.port.TechQuestionBankPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class DailyQuestionContentServiceTest {

    @Mock lateinit var dailyQuestionContentPort: DailyQuestionContentPort
    @Mock lateinit var techQuestionBankPort: TechQuestionBankPort
    @Mock lateinit var techInterviewPort: TechInterviewPort

    private lateinit var service: DailyQuestionContentService

    private fun newService() = DailyQuestionContentService(
        dailyQuestionContentPort = dailyQuestionContentPort,
        techQuestionBankPort = techQuestionBankPort,
        techInterviewPort = techInterviewPort,
        techStack = "Java,Spring Boot,JPA",
    )

    @Test
    fun `ensureTodayQuestion - 유저가 0명이어도 오늘의 질문이 없으면 뱅크에서 생성해 저장한다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any())).thenAnswer { it.arguments[0] }

        val result = service.ensureTodayQuestion()

        assertThat(result.question).isEqualTo("뱅크 질문")
        assertThat(result.source).isEqualTo("BANK")
        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
        verify(dailyQuestionContentPort).save(any())
    }

    @Test
    fun `ensureTodayQuestion - 뱅크가 소진되면 AI로 질문을 생성한다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull())).thenReturn(null)
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("AI 생성 질문")
        whenever(dailyQuestionContentPort.save(any())).thenAnswer { it.arguments[0] }

        val result = service.ensureTodayQuestion()

        assertThat(result.question).isEqualTo("AI 생성 질문")
        assertThat(result.source).isEqualTo("AI")
        verify(dailyQuestionContentPort).save(any())
    }

    @Test
    fun `ensureTodayQuestion - 오늘 질문이 이미 있으면 재생성하지 않고 그대로 반환한다`() {
        service = newService()
        val existing = DailyQuestionContent(
            id = 1L,
            questionDate = LocalDate.now(),
            mailType = "TECH_INTERVIEW",
            question = "기존 질문",
            source = "BANK",
        )
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(existing)

        val result = service.ensureTodayQuestion()

        assertThat(result).isEqualTo(existing)
        verify(techQuestionBankPort, never()).findUnused(any(), anyOrNull())
        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
        verify(dailyQuestionContentPort, never()).save(any())
    }

    @Test
    fun `ensureTodayQuestion - 두 번 호출해도 뱅크·AI 조회는 최초 1회만 일어난다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any()))
            .thenReturn(null)
            .thenReturn(DailyQuestionContent(question = "뱅크 질문", source = "BANK", mailType = "TECH_INTERVIEW"))
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any())).thenAnswer { it.arguments[0] }

        service.ensureTodayQuestion()
        service.ensureTodayQuestion()

        verify(techQuestionBankPort, org.mockito.kotlin.times(1)).findUnused(any(), anyOrNull())
        verify(dailyQuestionContentPort, org.mockito.kotlin.times(1)).save(any())
    }

    @Test
    fun `ensureTodayQuestion - 최근 질문 제외 목록을 20일 전 날짜를 기준으로 조회한다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any())).thenAnswer { it.arguments[0] }

        service.ensureTodayQuestion()

        val mailTypeCaptor = argumentCaptor<String>()
        val sinceCaptor = argumentCaptor<LocalDate>()
        verify(dailyQuestionContentPort).findQuestionsSince(mailTypeCaptor.capture(), sinceCaptor.capture())
        assertThat(mailTypeCaptor.firstValue).isEqualTo("TECH_INTERVIEW")
        assertThat(sinceCaptor.firstValue).isEqualTo(LocalDate.now().minusDays(20))
    }
}
