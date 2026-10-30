package com.devquest.core.api.support

import com.devquest.core.domain.port.RateLimitCounterPort
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 테스트용 [RateLimitCounterPort]. 정상일 때는 실제 어댑터와 같은 의미(상한 미만이면 +1, 환불은 0 아래로 안 내려감)를
 * 메모리에서 구현하고, [failing] 이 true 면 DB 장애처럼 매 호출이 예외를 던진다.
 *
 * [today] 는 이 "DB" 가 오늘로 보는 KST 날짜다 — 바꾸면 자정을 넘긴 것처럼 동작한다(행은 날짜별로 따로 센다).
 */
class FakeRateLimitCounterPort(@Volatile var failing: Boolean = false) : RateLimitCounterPort {

    @Volatile
    var today: LocalDate = LocalDate.of(2026, 10, 29)

    private val used = ConcurrentHashMap<Triple<String, String, LocalDate>, Int>()
    val consumeCalls = CopyOnWriteArrayList<Triple<String, String, Int>>()
    val refundCalls = CopyOnWriteArrayList<Pair<String, String>>()
    val refundDates = CopyOnWriteArrayList<LocalDate>()
    var deleteExpiredCalls = 0

    private val hold = AtomicReference<Hold?>(null)

    /** [holdNextConsume] 로 붙잡힌 호출의 신호. [entered] = 호출이 DB 안에 들어옴, [release] = 풀어 주기. */
    class Hold(val entered: CountDownLatch = CountDownLatch(1), val release: CountDownLatch = CountDownLatch(1))

    /** 다음 [tryConsume] 한 번을 [Hold.release] 가 열릴 때까지 붙잡는다(느린 DB 흉내). 그 뒤 호출은 그대로 통과한다. */
    fun holdNextConsume(): Hold = Hold().also { hold.set(it) }

    fun usedOf(scope: String, key: String, date: LocalDate = today): Int = used[Triple(scope, key, date)] ?: 0

    override fun tryConsume(scope: String, key: String, capacity: Int): LocalDate? {
        consumeCalls += Triple(scope, key, capacity)
        hold.getAndSet(null)?.let {
            it.entered.countDown()
            it.release.await(5, TimeUnit.SECONDS)
        }
        return consume(scope, key, capacity)
    }

    @Synchronized
    private fun consume(scope: String, key: String, capacity: Int): LocalDate? {
        if (failing) throw org.springframework.dao.DataAccessResourceFailureException("db down")
        val id = Triple(scope, key, today)
        val current = used[id] ?: 0
        if (current >= capacity) return null
        used[id] = current + 1
        return today
    }

    @Synchronized
    override fun refund(scope: String, key: String, date: LocalDate) {
        refundCalls += scope to key
        refundDates += date
        if (failing) throw org.springframework.dao.DataAccessResourceFailureException("db down")
        val id = Triple(scope, key, date)
        val current = used[id] ?: 0
        if (current > 0) used[id] = current - 1
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
