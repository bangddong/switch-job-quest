package com.devquest.core.api.scheduler

import com.devquest.core.domain.DailyQuestionContentService
import com.devquest.core.domain.MailService
import com.devquest.core.domain.port.DailyMailLogPort
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
    private val dailyQuestionContentService: DailyQuestionContentService,
    private val dailyMailLogPort: DailyMailLogPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    fun sendDailyTechInterviewMail() {
        // ① 콘텐츠 생성 — 유저 수·MAIL_ENABLED와 무관하게 항상 먼저 실행된다.
        // 아래의 early return(발송 대상 없음 등)이 콘텐츠 생성을 막아서는 안 된다.
        val content = dailyQuestionContentService.ensureTodayQuestion()
        val question = content.question

        // ② 발송 게이트 — 여기부터는 메일 발송에만 관여하고, 콘텐츠 생성에는 영향을 주지 않는다.
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
