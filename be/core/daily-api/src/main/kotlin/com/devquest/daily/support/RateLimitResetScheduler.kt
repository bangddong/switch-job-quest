package com.devquest.daily.support

import com.devquest.core.domain.port.RateLimitCounterPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * core-api `RateLimitResetScheduler`와 같은 자정 정리(D-007 — core-api를 의존할 수 없어 복제).
 * daily-api 는 daily explain/evaluate 의 메모리 대체 버킷과 전체 일일 상한 대체 버킷만 가진다.
 *
 * 공유 DB 카운터는 날짜가 키라 리셋이 필요 없고, 2일 지난 행만 멱등 DELETE 로 지운다
 * (core-api·다른 파드와 같은 시각에 돌려도 안전). 실패해도 스케줄러 스레드를 죽이지 않는다.
 */
@Component
class RateLimitResetScheduler(
    private val dailyExplainRateLimitBucketStore: DailyExplainRateLimitBucketStore,
    private val dailyEvaluateRateLimitBucketStore: DailyEvaluateRateLimitBucketStore,
    private val dailyExplainGlobalRateLimitStore: DailyExplainGlobalRateLimitStore,
    private val dailyEvaluateGlobalRateLimitStore: DailyEvaluateGlobalRateLimitStore,
    private val rateLimitCounterPort: RateLimitCounterPort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun resetDailyLimits() {
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
