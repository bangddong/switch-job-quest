package com.devquest.core.api.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RateLimitResetSchedulerTest {

    @Test
    fun `자정 리셋 시 tech-interview, daily-explain, daily-evaluate 개인 버킷과 전역 상한을 모두 초기화한다`() {
        val rateLimitBucketStore = RateLimitBucketStore(capacity = 2, refillDays = 1)
        val dailyExplainRateLimitBucketStore = DailyExplainRateLimitBucketStore(capacity = 5, refillDays = 1)
        val dailyEvaluateRateLimitBucketStore = DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1)
        val dailyExplainGlobalStore = DailyExplainGlobalRateLimitStore(capacity = 2, refillDays = 1)
        val dailyEvaluateGlobalStore = DailyEvaluateGlobalRateLimitStore(capacity = 3, refillDays = 1)
        val scheduler = RateLimitResetScheduler(
            rateLimitBucketStore,
            dailyExplainRateLimitBucketStore,
            dailyEvaluateRateLimitBucketStore,
            dailyExplainGlobalStore,
            dailyEvaluateGlobalStore,
            FakeRateLimitCounterPort(),
        )
        val ip = "1.2.3.4"

        exhaust(rateLimitBucketStore, ip, times = 2)
        exhaust(dailyExplainRateLimitBucketStore, ip, times = 5)
        exhaust(dailyEvaluateRateLimitBucketStore, ip, times = 1)

        assertThat(rateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isFalse()
        assertThat(dailyExplainRateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isFalse()
        assertThat(dailyEvaluateRateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isFalse()
        repeat(2) { dailyExplainGlobalStore.bucket().tryConsume(1) }
        repeat(3) { dailyEvaluateGlobalStore.bucket().tryConsume(1) }
        assertThat(dailyExplainGlobalStore.bucket().tryConsume(1)).isFalse()
        assertThat(dailyEvaluateGlobalStore.bucket().tryConsume(1)).isFalse()

        scheduler.resetDailyLimits()

        assertThat(rateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isTrue()
        assertThat(dailyExplainRateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isTrue()
        assertThat(dailyEvaluateRateLimitBucketStore.getOrCreate(ip).tryConsume(1)).isTrue()
        assertThat(dailyExplainGlobalStore.bucket().availableTokens).isEqualTo(2)
        assertThat(dailyEvaluateGlobalStore.bucket().availableTokens).isEqualTo(3)
    }

    @Test
    fun `자정에 만료된 DB 카운터 행 정리를 호출한다 - 멱등 DELETE 라 인스턴스마다 돌려도 된다`() {
        val port = FakeRateLimitCounterPort()

        newScheduler(port).resetDailyLimits()
        newScheduler(port).resetDailyLimits()

        assertThat(port.deleteExpiredCalls).isEqualTo(2)
    }

    @Test
    fun `DB 정리가 실패해도 예외를 밖으로 던지지 않고 메모리 버킷 리셋은 끝낸다`() {
        val port = FakeRateLimitCounterPort(failing = true)
        val store = DailyExplainRateLimitBucketStore(capacity = 1, refillDays = 1)
        store.getOrCreate("1.2.3.4").tryConsume(1)

        newScheduler(port, store).resetDailyLimits() // 던지면 스케줄러 스레드의 오류로 남는다

        assertThat(store.getOrCreate("1.2.3.4").tryConsume(1)).isTrue()
    }

    private fun newScheduler(
        port: FakeRateLimitCounterPort,
        explainStore: DailyExplainRateLimitBucketStore = DailyExplainRateLimitBucketStore(capacity = 5, refillDays = 1),
    ) = RateLimitResetScheduler(
        RateLimitBucketStore(capacity = 2, refillDays = 1),
        explainStore,
        DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1),
        DailyExplainGlobalRateLimitStore(capacity = 2, refillDays = 1),
        DailyEvaluateGlobalRateLimitStore(capacity = 3, refillDays = 1),
        port,
    )

    private fun exhaust(store: AbstractRateLimitBucketStore, ip: String, times: Int) {
        repeat(times) { store.getOrCreate(ip).tryConsume(1) }
    }
}
