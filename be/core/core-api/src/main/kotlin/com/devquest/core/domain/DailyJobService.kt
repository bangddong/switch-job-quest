package com.devquest.core.domain

import com.devquest.core.domain.port.DailyMailLogPort
import com.devquest.core.domain.port.DailyQuestionGeneratorPort
import com.devquest.core.domain.port.RateLimitCounterPort
import com.devquest.core.domain.port.UserEmailPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

data class DailyJobResult(
    val mailTargets: Int = 0,
    val mailSent: Int = 0,
    val expiredRateLimitRowsDeleted: Int = 0,
)

/**
 * 매일 09:00(KST) 작업 — Cloudflare Worker Cron 이 `POST /internal/jobs/daily` 로 깨운다.
 *
 * 예전에는 `DailyMailScheduler` 의 `@Scheduled` 였다. Fly 머신이 유휴 시 suspend 되면서 cron 이 안 돌아 HTTP 트리거로 옮겼다.
 * `@Scheduled` 를 남기지 않는 이유: 머신이 resume 되는 순간 밀린 cron 이 엔드포인트와 겹쳐 메일이 중복 발송된다
 * (메일 로그에 UNIQUE 가 없고 check-then-send 다).
 *
 * 작업
 * 1. 오늘의 질문 생성 — 항상 먼저. 아래 발송 게이트(early return)가 콘텐츠 생성을 막아서는 안 된다.
 * 2. 메일 발송 — 대상 필터(`existsTodayLog`)와 "발송 성공 뒤에만 로그 저장" 은 기존 로직 그대로.
 * 3. 만료된 레이트 리밋 행 정리 — 밤에 머신이 자면 00:00 `RateLimitResetScheduler` 가 안 돌아 만료 행이 쌓인다. 멱등 DELETE 라
 *    둘 다 돌아도 안전하다. 실패해도 작업을 실패시키지 않는다(다음 날 다시 지운다).
 *
 * **동기 실행**이다. 비동기로 두면 Fly 가 발송 중에 머신을 재울 수 있다(auto-stop 은 인바운드 연결만 본다).
 * 단일 실행 가드는 in-process 잠금 — Fly 는 1대라 충분하다(EKS 는 이 작업을 하지 않는다).
 */
@Service
class DailyJobService(
    private val userEmailPort: UserEmailPort,
    private val mailService: MailService,
    // Stage B-1: DailyQuestionContentService(구체 클래스, core:daily-core 모듈 소속)가 아니라 Port 를 주입한다 — 원장 L-27 해소.
    private val dailyQuestionGeneratorPort: DailyQuestionGeneratorPort,
    private val dailyMailLogPort: DailyMailLogPort,
    private val rateLimitCounterPort: RateLimitCounterPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = ReentrantLock()

    /**
     * @param scheduledTimeMillis Worker 가 보낸 예약 시각(epoch millis). 서버 시계와 5분 넘게 어긋나면 **경고만** 남기고
     *        서버 시계 기준으로 진행한다 — suspend 복원 직후 시계 의심을 관측하기 위한 값이다.
     * @return 결과. 이미 실행 중이면 null(아무것도 하지 않는다).
     */
    fun runDaily(scheduledTimeMillis: Long? = null): DailyJobResult? {
        if (!lock.tryLock()) {
            log.warn("일일 작업이 이미 실행 중 — 이번 요청은 건너뜁니다")
            return null
        }
        try {
            val now = System.currentTimeMillis()
            if (isClockSkewed(scheduledTimeMillis, now)) {
                log.warn(
                    "scheduledTime 이 서버 시계와 {}분 넘게 어긋남(scheduledTime={}, server={}) — 서버 시계 기준으로 진행합니다",
                    SKEW_THRESHOLD_MILLIS / 60_000, scheduledTimeMillis, now,
                )
            }
            // 메일 작업이 던져도 만료 행 정리는 시도한다. 예외는 정리 뒤에 그대로 전파한다(500 — 성공처럼 보이면 안 된다).
            val mail = runCatching { sendDailyTechInterviewMail() }
            val deleted = deleteExpiredCounters()
            val mailResult = mail.getOrThrow()
            return DailyJobResult(
                mailTargets = mailResult.first,
                mailSent = mailResult.second,
                expiredRateLimitRowsDeleted = deleted,
            )
        } finally {
            lock.unlock()
        }
    }

    /** @return (발송 대상 수, 발송 성공 수) */
    private fun sendDailyTechInterviewMail(): Pair<Int, Int> {
        // ① 콘텐츠 생성 — 유저 수·MAIL_ENABLED와 무관하게 항상 먼저 실행된다.
        // 아래의 early return(발송 대상 없음 등)이 콘텐츠 생성을 막아서는 안 된다.
        val content = dailyQuestionGeneratorPort.ensureTodayQuestion()
        val question = content.question

        // ② 발송 게이트 — 여기부터는 메일 발송에만 관여하고, 콘텐츠 생성에는 영향을 주지 않는다.
        val allUsers = userEmailPort.findAll()
        if (allUsers.isEmpty()) {
            log.info("발송 대상 없음 — 기술 면접 데일리 메일 skip")
            return 0 to 0
        }

        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val targets = allUsers.filter { (userId, _) ->
            !dailyMailLogPort.existsTodayLog(userId, "TECH_INTERVIEW", today)
        }
        if (targets.isEmpty()) {
            log.info("발송 대상 없음 — 오늘 이미 모든 사용자에게 발송 완료")
            return 0 to 0
        }

        val deepLink = "https://quest.dhbang.co.kr/daily-question"

        log.info("데일리 기술 면접 메일 발송 시작: 대상 수=${targets.size}")
        var sentCount = 0
        targets.forEach { (userId, email) ->
            runCatching {
                val sent = mailService.sendDailyTechInterview(to = email, question = question, deepLink = deepLink)
                if (sent) {
                    dailyMailLogPort.save(userId, "TECH_INTERVIEW", question, LocalDateTime.now())
                    sentCount++
                }
            }.onFailure { e ->
                log.warn("메일 발송 실패: userId=$userId, email=$email, error=${e.message}")
            }
        }
        log.info("데일리 기술 면접 메일 발송 완료: 성공=${sentCount}건")
        return targets.size to sentCount
    }

    private fun deleteExpiredCounters(): Int =
        try {
            val deleted = rateLimitCounterPort.deleteExpired()
            log.info("Rate limit counters expired rows deleted: {}", deleted)
            deleted
        } catch (e: Exception) {
            log.warn("레이트 리밋 카운터 만료 행 정리 실패 — 다음 날 다시 시도합니다: {}: {}", e.javaClass.simpleName, e.message)
            0
        }

    companion object {
        private const val SKEW_THRESHOLD_MILLIS = 5 * 60_000L

        /** 예약 시각이 서버 시계와 5분(경계 포함은 정상) 넘게 어긋났는가. null 이면 판단할 근거가 없으므로 false. */
        fun isClockSkewed(scheduledTimeMillis: Long?, nowMillis: Long): Boolean =
            scheduledTimeMillis != null && abs(nowMillis - scheduledTimeMillis) > SKEW_THRESHOLD_MILLIS
    }
}
