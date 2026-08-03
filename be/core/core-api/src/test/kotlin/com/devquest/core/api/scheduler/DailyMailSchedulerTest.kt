package com.devquest.core.api.scheduler

import com.devquest.core.domain.DailyQuestionContentService
import com.devquest.core.domain.MailService
import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.core.domain.port.DailyMailLogPort
import com.devquest.core.domain.port.UserEmailPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class DailyMailSchedulerTest {

    @Mock lateinit var userEmailPort: UserEmailPort
    @Mock lateinit var mailService: MailService
    @Mock lateinit var dailyQuestionContentService: DailyQuestionContentService
    @Mock lateinit var dailyMailLogPort: DailyMailLogPort

    private lateinit var scheduler: DailyMailScheduler

    private val defaultContent = DailyQuestionContent(
        questionDate = LocalDate.now(),
        mailType = "TECH_INTERVIEW",
        question = "오늘의 질문",
        source = "BANK",
    )

    @BeforeEach
    fun setUp() {
        scheduler = DailyMailScheduler(
            userEmailPort = userEmailPort,
            mailService = mailService,
            dailyQuestionContentService = dailyQuestionContentService,
            dailyMailLogPort = dailyMailLogPort,
        )
    }

    @Test
    fun `발송 대상이 없어도 오늘의 질문 생성은 항상 먼저 실행된다`() {
        // 콘텐츠 생성 → G-2: 유저 0명·MAIL_ENABLED=false와 무관하게 오늘의 질문이 생성돼야 한다.
        whenever(dailyQuestionContentService.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(emptyList())

        scheduler.sendDailyTechInterviewMail()

        verify(dailyQuestionContentService).ensureTodayQuestion()
        verify(mailService, never()).sendDailyTechInterview(any(), any(), any())
    }

    @Test
    fun `메일이 비활성화(MAIL_ENABLED=false)돼 발송이 전부 실패해도 오늘의 질문 생성은 이루어진다`() {
        // MailService.sendDailyTechInterview는 MAIL_ENABLED=false일 때 false를 반환한다(예외 아님).
        whenever(dailyQuestionContentService.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(false)
        whenever(mailService.sendDailyTechInterview(any(), any(), any())).thenReturn(false)

        scheduler.sendDailyTechInterviewMail()

        verify(dailyQuestionContentService).ensureTodayQuestion()
        verify(dailyMailLogPort, never()).save(any(), any(), any(), any())
    }

    @Test
    fun `오늘 이미 발송된 사용자는 메일을 skip한다`() {
        whenever(dailyQuestionContentService.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(true)

        scheduler.sendDailyTechInterviewMail()

        verify(mailService, never()).sendDailyTechInterview(any(), any(), any())
    }

    @Test
    fun `오늘 발송 이력이 없는 사용자에게 메일을 발송하고 로그를 저장한다`() {
        whenever(dailyQuestionContentService.ensureTodayQuestion())
            .thenReturn(defaultContent.copy(question = "오늘의 질문"))
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(false)
        whenever(mailService.sendDailyTechInterview(any(), any(), any())).thenReturn(true)

        scheduler.sendDailyTechInterviewMail()

        verify(mailService).sendDailyTechInterview(eq("user1@test.com"), eq("오늘의 질문"), any())
        verify(dailyMailLogPort).save(eq("user1"), eq("TECH_INTERVIEW"), eq("오늘의 질문"), any())
    }

    @Test
    fun `메일 발송 실패 시 로그 저장도 하지 않는다`() {
        whenever(dailyQuestionContentService.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(any(), any(), any<LocalDate>())).thenReturn(false)
        whenever(mailService.sendDailyTechInterview(any(), any(), any()))
            .thenThrow(RuntimeException("SMTP error"))

        scheduler.sendDailyTechInterviewMail()

        verify(dailyMailLogPort, never()).save(any<String>(), any<String>(), any<String>(), any())
    }

    @Test
    fun `콘텐츠 생성은 유저 조회·중복 필터보다 먼저 호출된다`() {
        whenever(dailyQuestionContentService.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(emptyList())

        scheduler.sendDailyTechInterviewMail()

        val inOrder = org.mockito.Mockito.inOrder(dailyQuestionContentService, userEmailPort)
        inOrder.verify(dailyQuestionContentService).ensureTodayQuestion()
        inOrder.verify(userEmailPort).findAll()
        assertThat(true).isTrue()
    }
}
