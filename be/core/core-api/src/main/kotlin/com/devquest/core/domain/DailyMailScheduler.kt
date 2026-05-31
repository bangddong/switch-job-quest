package com.devquest.core.domain

import com.devquest.core.domain.port.DailyMailLogPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.domain.port.UserEmailPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class DailyMailScheduler(
    private val userEmailPort: UserEmailPort,
    private val mailService: MailService,
    private val techInterviewPort: TechInterviewPort,
    private val dailyMailLogPort: DailyMailLogPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    fun sendDailyTechInterviewMail() {
        val allUsers = userEmailPort.findAll()
        if (allUsers.isEmpty()) {
            log.info("발송 대상 없음 — 기술 면접 데일리 메일 skip")
            return
        }

        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val question = techInterviewPort.generateDailyQuestion("Java,Spring Boot,JPA")
        val deepLink = "https://devquest.kr/tech-interview"

        log.info("데일리 기술 면접 메일 발송 시작: 대상 수=${allUsers.size}")
        allUsers.forEach { (userId, email) ->
            if (dailyMailLogPort.existsTodayLog(userId, "TECH_INTERVIEW", today)) {
                log.info("중복 발송 skip: userId=$userId")
                return@forEach
            }
            runCatching {
                mailService.sendDailyTechInterview(email, question, deepLink)
                dailyMailLogPort.save(userId, "TECH_INTERVIEW", question, LocalDateTime.now())
            }.onFailure { e ->
                log.warn("메일 발송 실패: userId=$userId, email=$email, error=${e.message}")
            }
        }
        log.info("데일리 기술 면접 메일 발송 완료")
    }
}
