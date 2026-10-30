package com.devquest.daily.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** core-api `RateLimitResetSchedulerTest`와 같은 정책(D-007 복제) — daily-api 는 explain/evaluate 만 가진다. */
class RateLimitResetSchedulerTest {

    @Test
    fun `자정 리셋 시 daily-explain, daily-evaluate 개인 버킷과 전역 상한을 모두 초기화한다`() {
        val explainStore = DailyExplainRateLimitBucketStore(capacity = 5, refillDays = 1)
        val evaluateStore = DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1)
        val explainGlobal = DailyExplainGlobalRateLimitStore(capacity = 2, refillDays = 1)
        val evaluateGlobal = DailyEvaluateGlobalRateLimitStore(capacity = 3, refillDays = 1)
        val scheduler = RateLimitResetScheduler(explainStore, evaluateStore, explainGlobal, evaluateGlobal, FakeRateLimitCounterPort())
        val ip = "1.2.3.4"

        repeat(5) { explainStore.getOrCreate(ip).tryConsume(1) }
        repeat(1) { evaluateStore.getOrCreate(ip).tryConsume(1) }
        repeat(2) { explainGlobal.bucket().tryConsume(1) }
        repeat(3) { evaluateGlobal.bucket().tryConsume(1) }
        assertThat(explainStore.getOrCreate(ip).tryConsume(1)).isFalse()
        assertThat(evaluateStore.getOrCreate(ip).tryConsume(1)).isFalse()
        assertThat(explainGlobal.bucket().tryConsume(1)).isFalse()
        assertThat(evaluateGlobal.bucket().tryConsume(1)).isFalse()

        scheduler.resetDailyLimits()

        assertThat(explainStore.getOrCreate(ip).tryConsume(1)).isTrue()
        assertThat(evaluateStore.getOrCreate(ip).tryConsume(1)).isTrue()
        assertThat(explainGlobal.bucket().availableTokens).isEqualTo(2)
        assertThat(evaluateGlobal.bucket().availableTokens).isEqualTo(3)
    }

    @Test
    fun `자정에 만료된 DB 카운터 행 정리를 호출한다 - 멱등 DELETE 라 파드마다 돌려도 된다`() {
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
        explainStore,
        DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1),
        DailyExplainGlobalRateLimitStore(capacity = 2, refillDays = 1),
        DailyEvaluateGlobalRateLimitStore(capacity = 3, refillDays = 1),
        port,
    )
}
