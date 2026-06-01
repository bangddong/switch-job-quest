package com.devquest.core.domain

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender

@ExtendWith(MockitoExtension::class)
class MailServiceTest {

    @Mock
    private lateinit var mailSender: JavaMailSender

    private val from = "noreply@dhbang.co.kr"

    private fun mailService(enabled: Boolean) = MailService(mailSender, from, enabled)

    private fun stubCreateMimeMessage() {
        whenever(mailSender.createMimeMessage()).thenReturn(MimeMessage(null as Session?))
    }

    // sendDailyTechInterview 테스트

    @Test
    fun `MAIL_ENABLED=false이면 mailSender를 호출하지 않고 false를 반환한다`() {
        val service = mailService(enabled = false)

        val result = service.sendDailyTechInterview(
            to = "user@test.com",
            question = "오늘의 질문",
            deepLink = "https://quest.dhbang.co.kr/tech-interview"
        )

        assertFalse(result)
        verify(mailSender, never()).send(any<MimeMessage>())
    }

    @Test
    fun `MAIL_ENABLED=true이면 mailSender를 호출하고 true를 반환한다`() {
        stubCreateMimeMessage()
        val service = mailService(enabled = true)

        val result = service.sendDailyTechInterview(
            to = "user@test.com",
            question = "오늘의 질문",
            deepLink = "https://quest.dhbang.co.kr/tech-interview"
        )

        assertTrue(result)
        verify(mailSender).send(any<MimeMessage>())
    }

    @Test
    fun `발송 시 mailSender에 MimeMessage를 전달한다`() {
        stubCreateMimeMessage()
        val service = mailService(enabled = true)

        service.sendDailyTechInterview(
            to = "user@test.com",
            question = "JPA N+1 문제를 설명하세요",
            deepLink = "https://quest.dhbang.co.kr/tech-interview"
        )

        verify(mailSender).send(any<MimeMessage>())
    }

    @Test
    fun `mailSender 예외 발생 시 호출자에게 예외를 전파한다`() {
        stubCreateMimeMessage()
        doThrow(MailSendException("SMTP 연결 실패")).`when`(mailSender).send(any<MimeMessage>())
        val service = mailService(enabled = true)

        assertThrows<MailSendException> {
            service.sendDailyTechInterview(
                to = "user@test.com",
                question = "오늘의 질문",
                deepLink = "https://quest.dhbang.co.kr/tech-interview"
            )
        }
    }

    // sendDailyCodingProblem 테스트

    @Test
    fun `코딩 문제 - MAIL_ENABLED=false이면 mailSender를 호출하지 않는다`() {
        val service = mailService(enabled = false)

        service.sendDailyCodingProblem(
            to = "user@test.com",
            problemTitle = "두 수의 합",
            description = "두 정수 a, b가 주어질 때 합을 반환하세요.",
            deepLink = "https://quest.dhbang.co.kr/coding"
        )

        verify(mailSender, never()).send(any<MimeMessage>())
    }

    @Test
    fun `코딩 문제 - MAIL_ENABLED=true이면 mailSender를 호출한다`() {
        stubCreateMimeMessage()
        val service = mailService(enabled = true)

        service.sendDailyCodingProblem(
            to = "user@test.com",
            problemTitle = "두 수의 합",
            description = "두 정수 a, b가 주어질 때 합을 반환하세요.",
            deepLink = "https://quest.dhbang.co.kr/coding"
        )

        verify(mailSender).send(any<MimeMessage>())
    }

    @Test
    fun `코딩 문제 발송 시 mailSender에 MimeMessage를 전달한다`() {
        stubCreateMimeMessage()
        val service = mailService(enabled = true)

        service.sendDailyCodingProblem(
            to = "user@test.com",
            problemTitle = "두 수의 합",
            description = "두 정수 a, b가 주어질 때 합을 반환하세요.",
            deepLink = "https://quest.dhbang.co.kr/coding"
        )

        verify(mailSender).send(any<MimeMessage>())
    }

    @Test
    fun `코딩 문제 - mailSender 예외 발생 시 호출자에게 예외를 전파한다`() {
        stubCreateMimeMessage()
        doThrow(MailSendException("SMTP 연결 실패")).`when`(mailSender).send(any<MimeMessage>())
        val service = mailService(enabled = true)

        assertThrows<MailSendException> {
            service.sendDailyCodingProblem(
                to = "user@test.com",
                problemTitle = "두 수의 합",
                description = "두 정수 a, b가 주어질 때 합을 반환하세요.",
                deepLink = "https://quest.dhbang.co.kr/coding"
            )
        }
    }
}
