package com.devquest.core.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.Mockito.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

@ExtendWith(MockitoExtension::class)
class MailServiceTest {

    @Mock
    private lateinit var mailSender: JavaMailSender

    private val from = "noreply@devquest.kr"

    private fun mailService(enabled: Boolean) = MailService(mailSender, from, enabled)

    @Test
    fun `MAIL_ENABLED=false이면 mailSender를 호출하지 않고 false를 반환한다`() {
        val service = mailService(enabled = false)

        val result = service.sendDailyTechInterview(
            to = "user@test.com",
            questionPreview = "오늘의 질문",
            deepLink = "https://devquest.kr/tech-interview"
        )

        assertFalse(result)
        verify(mailSender, never()).send(any<SimpleMailMessage>())
    }

    @Test
    fun `MAIL_ENABLED=true이면 mailSender를 호출하고 true를 반환한다`() {
        val service = mailService(enabled = true)

        val result = service.sendDailyTechInterview(
            to = "user@test.com",
            questionPreview = "오늘의 질문",
            deepLink = "https://devquest.kr/tech-interview"
        )

        assertTrue(result)
        verify(mailSender).send(any<SimpleMailMessage>())
    }

    @Test
    fun `발송 메시지에 수신자, 발신자, 제목, 질문 내용이 포함된다`() {
        val service = mailService(enabled = true)
        val captor = argumentCaptor<SimpleMailMessage>()

        service.sendDailyTechInterview(
            to = "user@test.com",
            questionPreview = "JPA N+1 문제를 설명하세요",
            deepLink = "https://devquest.kr/tech-interview"
        )

        verify(mailSender).send(captor.capture())
        val message = captor.firstValue

        assertTrue(message.to?.contains("user@test.com") == true)
        assertTrue(message.from == from)
        assertTrue(message.subject?.contains("[DevQuest]") == true)
        assertTrue(message.text?.contains("JPA N+1 문제를 설명하세요") == true)
        assertTrue(message.text?.contains("https://devquest.kr/tech-interview") == true)
    }

    @Test
    fun `mailSender 예외 발생 시 호출자에게 예외를 전파한다`() {
        val service = mailService(enabled = true)
        doThrow(MailSendException("SMTP 연결 실패")).`when`(mailSender).send(any<SimpleMailMessage>())

        assertThrows<MailSendException> {
            service.sendDailyTechInterview(
                to = "user@test.com",
                questionPreview = "오늘의 질문",
                deepLink = "https://devquest.kr/tech-interview"
            )
        }
    }
}
