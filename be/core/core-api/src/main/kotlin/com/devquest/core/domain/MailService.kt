package com.devquest.core.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class MailService(
    private val mailSender: JavaMailSender,
    @Value("\${devquest.mail.from}") private val from: String,
    @Value("\${devquest.mail.enabled}") private val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendDailyTechInterview(to: String, questionPreview: String, deepLink: String): Boolean {
        if (!enabled) {
            log.info("메일 발송 skip (MAIL_ENABLED=false): to=$to, type=tech-interview")
            return false
        }
        val message = SimpleMailMessage().apply {
            setFrom(this@MailService.from)
            setTo(to)
            subject = "[DevQuest] 오늘의 기술 면접 질문이 도착했어요!"
            text = """
                안녕하세요! DevQuest 데일리 기술 면접 질문입니다.

                오늘의 질문: $questionPreview

                지금 바로 도전하세요: $deepLink

                매일 꾸준히 연습하면 이직 성공에 한 걸음 더 가까워집니다!
            """.trimIndent()
        }
        mailSender.send(message)
        log.info("기술 면접 메일 발송 완료: to=$to")
        return true
    }

    fun sendDailyCodingProblem(to: String, problemTitle: String, deepLink: String) {
        if (!enabled) {
            log.info("메일 발송 skip (MAIL_ENABLED=false): to=$to, type=coding-problem")
            return
        }
        val message = SimpleMailMessage().apply {
            setFrom(this@MailService.from)
            setTo(to)
            subject = "[DevQuest] 오늘의 코딩 문제가 도착했어요!"
            text = """
                안녕하세요! DevQuest 데일리 코딩 문제입니다.

                오늘의 문제: $problemTitle

                지금 바로 도전하세요: $deepLink

                매일 꾸준히 풀면 알고리즘 실력이 쑥쑥 늘어납니다!
            """.trimIndent()
        }
        mailSender.send(message)
        log.info("코딩 문제 메일 발송 완료: to=$to")
    }
}
