package com.devquest.daily.support

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * core-api `RateLimitResetScheduler`와 같은 자정 리셋(D-007 — core-api를 의존할 수 없어 복제).
 * daily-api 는 daily explain/evaluate 의 IP 개인 버킷과 전체 일일 상한만 가진다.
 */
@Component
class RateLimitResetScheduler(
    private val dailyExplainRateLimitBucketStore: DailyExplainRateLimitBucketStore,
    private val dailyEvaluateRateLimitBucketStore: DailyEvaluateRateLimitBucketStore,
    private val dailyExplainGlobalRateLimitStore: DailyExplainGlobalRateLimitStore,
    private val dailyEvaluateGlobalRateLimitStore: DailyEvaluateGlobalRateLimitStore,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun resetDailyLimits() {
        dailyExplainRateLimitBucketStore.clear()
        dailyEvaluateRateLimitBucketStore.clear()
        dailyExplainGlobalRateLimitStore.clear()
        dailyEvaluateGlobalRateLimitStore.clear()
        log.info("Rate limit buckets cleared (daily reset)")
    }
}
