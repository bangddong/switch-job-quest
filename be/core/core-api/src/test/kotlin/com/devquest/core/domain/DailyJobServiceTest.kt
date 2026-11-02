package com.devquest.core.domain

import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.core.domain.port.DailyMailLogPort
import com.devquest.core.domain.port.DailyQuestionGeneratorPort
import com.devquest.core.domain.port.RateLimitCounterPort
import com.devquest.core.domain.port.UserEmailPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.springframework.scheduling.annotation.Scheduled
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 구 `DailyMailSchedulerTest` 를 옮겨 온 것이다 — 09:00 `@Scheduled` 메일이 HTTP 트리거(`POST /internal/jobs/daily`)로 바뀌면서
 * 로직이 [DailyJobService] 로 이사했다. 발송 게이트·콘텐츠 생성 순서 검증은 그대로 유지한다.
 */
@ExtendWith(MockitoExtension::class)
class DailyJobServiceTest {

    @Mock lateinit var userEmailPort: UserEmailPort
    @Mock lateinit var mailService: MailService
    @Mock lateinit var dailyQuestionGeneratorPort: DailyQuestionGeneratorPort
    @Mock lateinit var dailyMailLogPort: DailyMailLogPort
    @Mock lateinit var rateLimitCounterPort: RateLimitCounterPort

    private lateinit var service: DailyJobService

    private val defaultContent = DailyQuestionContent(
        questionDate = LocalDate.now(),
        mailType = "TECH_INTERVIEW",
        question = "오늘의 질문",
        source = "BANK",
    )

    @BeforeEach
    fun setUp() {
        service = DailyJobService(
            userEmailPort = userEmailPort,
            mailService = mailService,
            dailyQuestionGeneratorPort = dailyQuestionGeneratorPort,
            dailyMailLogPort = dailyMailLogPort,
            rateLimitCounterPort = rateLimitCounterPort,
        )
    }

    @Test
    fun `발송 대상이 없어도 오늘의 질문 생성은 항상 먼저 실행된다`() {
        // 콘텐츠 생성 → G-2: 유저 0명·MAIL_ENABLED=false와 무관하게 오늘의 질문이 생성돼야 한다.
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(emptyList())

        val result = service.runDaily()

        verify(dailyQuestionGeneratorPort).ensureTodayQuestion()
        verify(mailService, never()).sendDailyTechInterview(any(), any(), any())
        assertThat(result).isNotNull
        assertThat(result?.mailTargets).isEqualTo(0)
        assertThat(result?.mailSent).isEqualTo(0)
    }

    @Test
    fun `메일이 비활성화(MAIL_ENABLED=false)돼 발송이 전부 실패해도 오늘의 질문 생성은 이루어진다`() {
        // MailService.sendDailyTechInterview는 MAIL_ENABLED=false일 때 false를 반환한다(예외 아님).
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(false)
        whenever(mailService.sendDailyTechInterview(any(), any(), any())).thenReturn(false)

        val result = service.runDaily()

        verify(dailyQuestionGeneratorPort).ensureTodayQuestion()
        verify(dailyMailLogPort, never()).save(any(), any(), any(), any())
        assertThat(result?.mailTargets).isEqualTo(1)
        assertThat(result?.mailSent).isEqualTo(0)
    }

    @Test
    fun `오늘 이미 발송된 사용자는 메일을 skip한다`() {
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(true)

        val result = service.runDaily()

        verify(mailService, never()).sendDailyTechInterview(any(), any(), any())
        assertThat(result?.mailTargets).isEqualTo(0)
        assertThat(result?.mailSent).isEqualTo(0)
    }

    @Test
    fun `오늘 발송 이력이 없는 사용자에게 메일을 발송하고 로그를 저장한다`() {
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion())
            .thenReturn(defaultContent.copy(question = "오늘의 질문"))
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(eq("user1"), eq("TECH_INTERVIEW"), any<LocalDate>()))
            .thenReturn(false)
        whenever(mailService.sendDailyTechInterview(any(), any(), any())).thenReturn(true)

        val result = service.runDaily()

        verify(mailService).sendDailyTechInterview(eq("user1@test.com"), eq("오늘의 질문"), any())
        verify(dailyMailLogPort).save(eq("user1"), eq("TECH_INTERVIEW"), eq("오늘의 질문"), any())
        assertThat(result?.mailTargets).isEqualTo(1)
        assertThat(result?.mailSent).isEqualTo(1)
    }

    @Test
    fun `메일 발송 실패 시 로그 저장도 하지 않는다`() {
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(any(), any(), any<LocalDate>())).thenReturn(false)
        whenever(mailService.sendDailyTechInterview(any(), any(), any()))
            .thenThrow(RuntimeException("SMTP error"))

        val result = service.runDaily()

        verify(dailyMailLogPort, never()).save(any<String>(), any<String>(), any<String>(), any())
        assertThat(result?.mailTargets).isEqualTo(1)
        assertThat(result?.mailSent).isEqualTo(0)
    }

    @Test
    fun `콘텐츠 생성은 유저 조회·중복 필터보다 먼저 호출된다`() {
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(emptyList())

        service.runDaily()

        val inOrder = org.mockito.Mockito.inOrder(dailyQuestionGeneratorPort, userEmailPort)
        inOrder.verify(dailyQuestionGeneratorPort).ensureTodayQuestion()
        inOrder.verify(userEmailPort).findAll()
    }

    @Test
    fun `만료된 레이트 리밋 행을 지우고 지운 수를 결과에 담는다`() {
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(emptyList())
        whenever(rateLimitCounterPort.deleteExpired()).thenReturn(7)

        val result = service.runDaily()

        verify(rateLimitCounterPort).deleteExpired()
        assertThat(result?.expiredRateLimitRowsDeleted).isEqualTo(7)
    }

    @Test
    fun `deleteExpired 가 예외를 던져도 작업은 실패하지 않고 0 으로 보고한다`() {
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(listOf(Pair("user1", "user1@test.com")))
        whenever(dailyMailLogPort.existsTodayLog(any(), any(), any<LocalDate>())).thenReturn(false)
        whenever(mailService.sendDailyTechInterview(any(), any(), any())).thenReturn(true)
        whenever(rateLimitCounterPort.deleteExpired()).thenThrow(RuntimeException("DB down"))

        val result = service.runDaily()

        assertThat(result).isNotNull
        assertThat(result?.mailSent).isEqualTo(1)
        assertThat(result?.expiredRateLimitRowsDeleted).isEqualTo(0)
    }

    @Test
    fun `질문 생성이 실패하면 예외가 전파되지만 만료 행 정리는 시도하고 이후 잠금이 풀려 재실행할 수 있다`() {
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion())
            .thenThrow(RuntimeException("AI down"))
            .thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(emptyList())

        assertThatThrownBy { service.runDaily() }.hasMessageContaining("AI down")
        verify(rateLimitCounterPort).deleteExpired()

        // 잠금이 finally 로 풀렸다면 두 번째 호출은 null(실행 중)이 아니라 결과를 낸다.
        assertThat(service.runDaily()).isNotNull
    }

    @Test
    fun `이미 실행 중이면 두 번째 호출은 아무것도 하지 않고 null 을 돌려준다`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion()).thenAnswer {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            defaultContent
        }
        whenever(userEmailPort.findAll()).thenReturn(emptyList())

        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit<DailyJobResult?> { service.runDaily() }
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()

            val second = service.runDaily()

            assertThat(second).isNull()
            release.countDown()
            assertThat(first.get(5, TimeUnit.SECONDS)).isNotNull
            // 두 번째 호출은 질문 생성조차 시도하지 않았다(첫 호출의 1회뿐).
            verify(dailyQuestionGeneratorPort).ensureTodayQuestion()
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `서버 시계와 5분 넘게 차이 나는 scheduledTime 만 시계 이상으로 판정한다`() {
        val now = 1_700_000_000_000L
        assertThat(DailyJobService.isClockSkewed(scheduledTimeMillis = now, nowMillis = now)).isFalse()
        assertThat(DailyJobService.isClockSkewed(now - 5 * 60_000L, now)).isFalse()
        assertThat(DailyJobService.isClockSkewed(now - 5 * 60_000L - 1, now)).isTrue()
        assertThat(DailyJobService.isClockSkewed(now + 5 * 60_000L + 1, now)).isTrue()
        assertThat(DailyJobService.isClockSkewed(null, now)).isFalse()
    }

    @Test
    fun `scheduledTime 이 어긋나 있어도 서버 시계 기준으로 그대로 진행한다`() {
        whenever(dailyQuestionGeneratorPort.ensureTodayQuestion()).thenReturn(defaultContent)
        whenever(userEmailPort.findAll()).thenReturn(emptyList())

        val result = service.runDaily(scheduledTimeMillis = 1L)

        assertThat(result).isNotNull
        verify(dailyQuestionGeneratorPort).ensureTodayQuestion()
    }

    @Test
    fun `메일 작업은 더 이상 Scheduled 로 돌지 않는다 - 서비스에 Scheduled 메서드가 없고 옛 스케줄러 클래스가 없다`() {
        // resume 순간 밀린 cron 과 HTTP 트리거가 겹치면 메일이 중복 발송된다(메일 로그 UNIQUE 없음).
        val scheduled = DailyJobService::class.java.declaredMethods.filter { it.isAnnotationPresent(Scheduled::class.java) }
        assertThat(scheduled).isEmpty()
        assertThatThrownBy { Class.forName("com.devquest.core.api.scheduler.DailyMailScheduler") }
            .isInstanceOf(ClassNotFoundException::class.java)
    }
}
