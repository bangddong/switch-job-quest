package com.devquest.core.api.support

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RateLimitResetScheduler(
    private val rateLimitBucketStore: RateLimitBucketStore,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun resetDailyLimits() {
        rateLimitBucketStore.clear()
        log.info("Rate limit buckets cleared (daily reset)")
    }
}
