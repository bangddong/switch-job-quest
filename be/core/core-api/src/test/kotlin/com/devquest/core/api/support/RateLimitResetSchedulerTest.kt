package com.devquest.core.api.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RateLimitResetSchedulerTest {

    @Test
    fun `자정 리셋 시 tech-interview, daily-explain, daily-evaluate 버킷을 모두 초기화한다`() {
        val rateLimitBucketStore = RateLimitBucketStore(capacity = 2, refillDays = 1)
        val dailyExplainRateLimitBucketStore = DailyExplainRateLimitBucketStore(capacity = 5, refillDays = 1)
        val dailyEvaluateRateLimitBucketStore = DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1)
        val scheduler = RateLimitResetScheduler(
            rateLimitBucketStore,
            dailyExplainRateLimitBucketStore,
            dailyEvaluateRateLimitBucketStore,
        )
        val ip = "1.2.3.4"

        exhaust(rateLimitBucketStore, ip, times = 2)
        exhaust(dailyExplainRateLimitBucketStore, ip, times = 5)
        exhaust(dailyEvaluateRateLimitBucketStore, ip, times = 1)

        assertThat(rateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isFalse()
        assertThat(dailyExplainRateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isFalse()
        assertThat(dailyEvaluateRateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isFalse()

        scheduler.resetDailyLimits()

        assertThat(rateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isTrue()
        assertThat(dailyExplainRateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isTrue()
        assertThat(dailyEvaluateRateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isTrue()
    }

    private fun exhaust(store: AbstractRateLimitBucketStore, ip: String, times: Int) {
        repeat(times) { store.getOrCreate(ip).tryConsume(1) }
    }
}
