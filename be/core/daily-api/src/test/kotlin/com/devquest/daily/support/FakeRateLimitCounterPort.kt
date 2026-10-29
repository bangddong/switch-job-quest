package com.devquest.daily.support

import com.devquest.core.domain.port.RateLimitCounterPort
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * 테스트용 [RateLimitCounterPort]. 정상일 때는 실제 어댑터와 같은 의미(상한 미만이면 +1, 환불은 0 아래로 안 내려감)를
 * 메모리에서 구현하고, [failing] 이 true 면 DB 장애처럼 매 호출이 예외를 던진다.
 */
class FakeRateLimitCounterPort(@Volatile var failing: Boolean = false) : RateLimitCounterPort {

    private val used = ConcurrentHashMap<Pair<String, String>, Int>()
    val consumeCalls = mutableListOf<Triple<String, String, Int>>()
    val refundCalls = mutableListOf<Pair<String, String>>()
    var deleteExpiredCalls = 0

    fun usedOf(scope: String, key: String): Int = used[scope to key] ?: 0

    @Synchronized
    override fun tryConsume(scope: String, key: String, capacity: Int): Boolean {
        consumeCalls += Triple(scope, key, capacity)
        if (failing) throw org.springframework.dao.DataAccessResourceFailureException("db down")
        val current = used[scope to key] ?: 0
        if (current >= capacity) return false
        used[scope to key] = current + 1
        return true
    }

    @Synchronized
    override fun refund(scope: String, key: String) {
        refundCalls += scope to key
        if (failing) throw org.springframework.dao.DataAccessResourceFailureException("db down")
        val current = used[scope to key] ?: 0
        if (current > 0) used[scope to key] = current - 1
    }

    override fun deleteExpired(): Int {
        deleteExpiredCalls++
        if (failing) throw org.springframework.dao.DataAccessResourceFailureException("db down")
        return 0
    }
}

/** 시간을 직접 움직이는 테스트 시계 (서킷 대기 시간 검증용). */
class MutableClock(private var instant: Instant = Instant.parse("2026-10-29T01:00:00Z")) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId) = this
    override fun instant(): Instant = instant
    fun advanceMillis(millis: Long) { instant = instant.plusMillis(millis) }
}

/** DB 가 항상 실패하는 한도 검사기 — 기존 메모리 버킷 동작(대체 경로)을 그대로 검증하는 테스트가 쓴다. */
fun memoryOnlyLimiter(): SharedRateLimiter = SharedRateLimiter(FakeRateLimitCounterPort(failing = true))
