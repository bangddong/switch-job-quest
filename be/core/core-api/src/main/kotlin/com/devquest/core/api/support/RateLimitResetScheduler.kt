package com.devquest.core.api.support

import com.devquest.core.domain.port.RateLimitCounterPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 자정(Asia/Seoul)에 하는 레이트 리밋 정리.
 *
 * - 공유 DB 카운터는 날짜가 키라 **리셋이 필요 없다** — 대신 2일 지난 행을 지운다. 여러 인스턴스(Fly·EKS 파드, core-api·daily-api)가
 *   같은 시각에 돌려도 안전한 멱등 DELETE 다. 실패해도 스케줄러 스레드를 죽이지 않는다(다음 날 다시 지운다).
 * - 메모리 대체 버킷은 DB 장애 시에만 쓰이지만 refill 주기가 하루라 자정에 비우는 의미는 그대로다.
 */
@Component
class RateLimitResetScheduler(
    private val rateLimitBucketStore: RateLimitBucketStore,
    private val dailyExplainRateLimitBucketStore: DailyExplainRateLimitBucketStore,
    private val dailyEvaluateRateLimitBucketStore: DailyEvaluateRateLimitBucketStore,
    private val dailyExplainGlobalRateLimitStore: DailyExplainGlobalRateLimitStore,
    private val dailyEvaluateGlobalRateLimitStore: DailyEvaluateGlobalRateLimitStore,
    private val rateLimitCounterPort: RateLimitCounterPort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun resetDailyLimits() {
        rateLimitBucketStore.clear()
        dailyExplainRateLimitBucketStore.clear()
        dailyEvaluateRateLimitBucketStore.clear()
        dailyExplainGlobalRateLimitStore.clear()
        dailyEvaluateGlobalRateLimitStore.clear()
        log.info("Rate limit memory fallback buckets cleared (daily reset)")
        deleteExpiredCounters()
    }

    private fun deleteExpiredCounters() {
        try {
            val deleted = rateLimitCounterPort.deleteExpired()
            log.info("Rate limit counters expired rows deleted: {}", deleted)
        } catch (e: Exception) {
            log.warn("레이트 리밋 카운터 만료 행 정리 실패 — 다음 날 다시 시도합니다: {}: {}", e.javaClass.simpleName, e.message)
        }
    }
}
