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
        val scheduler = RateLimitResetScheduler(explainStore, evaluateStore, explainGlobal, evaluateGlobal)
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
}
