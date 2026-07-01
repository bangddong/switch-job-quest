package com.devquest.core.api.scheduler

import com.devquest.core.domain.MailService
import com.devquest.core.domain.port.DailyMailLogPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.domain.port.TechQuestionBankPort
import com.devquest.core.domain.port.UserEmailPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    private val techQuestionBankPort: TechQuestionBankPort,
    private val dailyMailLogPort: DailyMailLogPort,
    @Value("\${devquest.daily-question.tech-stack}") private val techStack: String,
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
        val targets = allUsers.filter { (userId, _) ->
            !dailyMailLogPort.existsTodayLog(userId, "TECH_INTERVIEW", today)
        }
        if (targets.isEmpty()) {
            log.info("발송 대상 없음 — 오늘 이미 모든 사용자에게 발송 완료")
            return
        }

        val recentQuestions = dailyMailLogPort.findRecentQuestions("TECH_INTERVIEW", 30)
        val bankQuestion = techQuestionBankPort.findUnused(recentQuestions)
        val question = if (bankQuestion != null) {
            log.info("질문 뱅크에서 질문 채택: category=${bankQuestion.category}")
            bankQuestion.question
        } else {
            log.info("질문 뱅크 소진 — AI로 질문 생성")
            techInterviewPort.generateDailyQuestion(techStack, recentQuestions)
        }
        val deepLink = "https://quest.dhbang.co.kr/daily-question"

        log.info("데일리 기술 면접 메일 발송 시작: 대상 수=${targets.size}")
        targets.forEach { (userId, email) ->
            runCatching {
                val sent = mailService.sendDailyTechInterview(to = email, question = question, deepLink = deepLink)
                if (sent) {
                    dailyMailLogPort.save(userId, "TECH_INTERVIEW", question, LocalDateTime.now())
                }
            }.onFailure { e ->
                log.warn("메일 발송 실패: userId=$userId, email=$email, error=${e.message}")
            }
        }
        log.info("데일리 기술 면접 메일 발송 완료")
    }
}
