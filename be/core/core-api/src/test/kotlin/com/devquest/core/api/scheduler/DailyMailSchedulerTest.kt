package com.devquest.core.api.scheduler

import com.devquest.core.domain.MailService
import com.devquest.core.domain.model.TechQuestionBank
import com.devquest.core.domain.port.DailyMailLogPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.domain.port.TechQuestionBankPort
import com.devquest.core.domain.port.UserEmailPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class DailyMailSchedulerTest {

    @Mock lateinit var userEmailPort: UserEmailPort
    @Mock lateinit var mailService: MailService
    @Mock lateinit var techInterviewPort: TechInterviewPort
    @Mock lateinit var techQuestionBankPort: TechQuestionBankPort
    @Mock lateinit var dailyMailLogPort: DailyMailLogPort

    private lateinit var scheduler: DailyMailScheduler

    @BeforeEach
    fun setUp() {
        scheduler = DailyMailScheduler(
            userEmailPort = userEmailPort,
            mailService = mailService,
            techInterviewPort = techInterviewPort,
            techQuestionBankPort = techQuestionBankPort,
            dailyMailLogPort = dailyMailLogPort,
            techStack = "Java,Spring Boot,JPA,네트워크,OS,자료구조,시스템 설계",
        )
    }

    @Test
    fun `발송 대상이 없으면 AI 질문 생성 및 메일 발송을 하지 않는다`() {
        whenever(userEmailPort.findAll()).thenReturn(emptyList())

        scheduler.sendDailyTechInterviewMail()

        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
        verify(mailService, never()).sendDailyTechInterview(any(), any(), any())
    }

    @Test
    fun `오늘 이미 발송된 사용자는 메일을 skip한다`() {
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(true)

        scheduler.sendDailyTechInterviewMail()

        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
        verify(mailService, never()).sendDailyTechInterview(any(), any(), any())
    }

    @Test
    fun `오늘 발송 이력이 없는 사용자에게 메일을 발송하고 로그를 저장한다`() {
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.findRecentQuestions(any(), any())).thenReturn(emptyList())
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("오늘의 질문")
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(false)
        whenever(mailService.sendDailyTechInterview(any(), any(), any())).thenReturn(true)

        scheduler.sendDailyTechInterviewMail()

        verify(mailService).sendDailyTechInterview(eq("user1@test.com"), eq("오늘의 질문"), any())
        verify(dailyMailLogPort).save(eq("user1"), eq("TECH_INTERVIEW"), eq("오늘의 질문"), any())
    }

    @Test
    fun `질문 뱅크에 미사용 질문이 있으면 뱅크 질문을 사용하고 AI 호출을 하지 않는다`() {
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.findRecentQuestions(any(), any())).thenReturn(emptyList())
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(false)
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(mailService.sendDailyTechInterview(any(), any(), any())).thenReturn(true)

        scheduler.sendDailyTechInterviewMail()

        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
        verify(mailService).sendDailyTechInterview(eq("user1@test.com"), eq("뱅크 질문"), any())
        verify(dailyMailLogPort).save(eq("user1"), eq("TECH_INTERVIEW"), eq("뱅크 질문"), any())
    }

    @Test
    fun `질문 뱅크가 소진되면 AI로 질문을 생성하는 폴백을 사용한다`() {
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.findRecentQuestions(any(), any())).thenReturn(emptyList())
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(false)
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull())).thenReturn(null)
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("AI 생성 질문")
        whenever(mailService.sendDailyTechInterview(any(), any(), any())).thenReturn(true)

        scheduler.sendDailyTechInterviewMail()

        verify(mailService).sendDailyTechInterview(eq("user1@test.com"), eq("AI 생성 질문"), any())
        verify(dailyMailLogPort).save(eq("user1"), eq("TECH_INTERVIEW"), eq("AI 생성 질문"), any())
    }

    @Test
    fun `메일 발송 실패 시 로그 저장도 하지 않는다`() {
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.findRecentQuestions(any(), any())).thenReturn(emptyList())
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("오늘의 질문")
        whenever(dailyMailLogPort.existsTodayLog(any(), any(), any<LocalDate>())).thenReturn(false)
        whenever(mailService.sendDailyTechInterview(any(), any(), any()))
            .thenThrow(RuntimeException("SMTP error"))

        scheduler.sendDailyTechInterviewMail()

        verify(dailyMailLogPort, never()).save(any<String>(), any<String>(), any<String>(), any())
    }
}
