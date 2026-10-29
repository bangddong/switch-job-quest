package com.devquest.core.api.support

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 공유 DB 카운터 → 메모리 버킷 대체(fail-soft) 정책.
 * DB 가 건강하면 DB 만 보고(메모리 버킷을 만들지 않는다), 실패하면 같은 정책의 메모리 버킷으로 판정한다.
 */
class SharedRateLimiterTest {

    private val port = FakeRateLimitCounterPort()
    private val clock = MutableClock()
    private val limiter = SharedRateLimiter(port, clock)

    private val logger = LoggerFactory.getLogger(SharedRateLimiter::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
    }

    private fun bucket(capacity: Long): Bucket = Bucket.builder()
        .addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity, Duration.ofDays(1)).build())
        .build()

    private fun noFallback(): Bucket = throw AssertionError("DB 가 정상이면 메모리 버킷은 만들어지지 않아야 한다")

    private val warns get() = appender.list.filter { it.level == Level.WARN }

    @Test
    fun `DB 가 정상이면 DB 로 판정하고 메모리 버킷은 건드리지 않는다`() {
        assertThat(limiter.tryConsume("s", "k", 2, ::noFallback)).isNotNull()
        assertThat(limiter.tryConsume("s", "k", 2, ::noFallback)).isNotNull()
        assertThat(limiter.tryConsume("s", "k", 2, ::noFallback)).isNull()

        assertThat(port.usedOf("s", "k")).isEqualTo(2)
    }

    @Test
    fun `티켓을 환불하면 DB 사용량이 되돌아간다`() {
        val ticket = limiter.tryConsume("s", "k", 1, ::noFallback)!!
        assertThat(limiter.tryConsume("s", "k", 1, ::noFallback)).isNull()

        ticket.refund()

        assertThat(port.usedOf("s", "k")).isEqualTo(0)
        assertThat(limiter.tryConsume("s", "k", 1, ::noFallback)).isNotNull()
    }

    @Test
    fun `DB 가 실패하면 같은 정책의 메모리 버킷으로 판정한다`() {
        port.failing = true
        val memory = bucket(2)

        assertThat(limiter.tryConsume("s", "k", 2) { memory }).isNotNull()
        assertThat(limiter.tryConsume("s", "k", 2) { memory }).isNotNull()
        assertThat(limiter.tryConsume("s", "k", 2) { memory }).isNull()
    }

    @Test
    fun `메모리 대체 경로의 티켓 환불은 메모리 버킷에 토큰을 돌려준다`() {
        port.failing = true
        val memory = bucket(1)
        val ticket = limiter.tryConsume("s", "k", 1) { memory }!!
        assertThat(memory.availableTokens).isEqualTo(0)

        ticket.refund()

        assertThat(memory.availableTokens).isEqualTo(1)
    }

    @Test
    fun `DB 에서 차감한 티켓은 환불 때 DB 가 죽었어도 예외 없이 넘어간다`() {
        val ticket = limiter.tryConsume("s", "k", 1, ::noFallback)!!
        port.failing = true

        ticket.refund() // 던지면 요청이 500 이 된다

        assertThat(warns).hasSize(1)
    }

    @Test
    fun `DB 장애 WARN 은 상태가 바뀔 때 한 번만 남기고 요청마다 남기지 않는다`() {
        port.failing = true
        val memory = bucket(1_000)

        repeat(50) {
            clock.advanceMillis(10_000) // 서킷 대기 시간을 넘겨 매번 DB 를 다시 시도하게 한다
            limiter.tryConsume("s", "k", 1_000) { memory }
        }

        assertThat(warns).hasSize(1)
        assertThat(port.consumeCalls).hasSize(50)
    }

    @Test
    fun `실패 직후에는 잠시 DB 를 건너뛰고 메모리로 간다 - 장애 중 요청마다 DB 대기를 하지 않는다`() {
        port.failing = true
        val memory = bucket(1_000)
        limiter.tryConsume("s", "k", 1_000) { memory }
        assertThat(port.consumeCalls).hasSize(1)

        repeat(20) { limiter.tryConsume("s", "k", 1_000) { memory } }
        assertThat(port.consumeCalls).describedAs("서킷이 열려 있는 동안 DB 호출 없음").hasSize(1)

        clock.advanceMillis(SharedRateLimiter.DB_RETRY_AFTER_MILLIS + 1)
        limiter.tryConsume("s", "k", 1_000) { memory }
        assertThat(port.consumeCalls).hasSize(2)
    }

    @Test
    fun `서킷 만료 후에는 한 요청만 DB 를 시도하고 나머지는 그동안 메모리로 간다 - half-open (QA F-2)`() {
        port.failing = true
        val memory = bucket(1_000)
        limiter.tryConsume("s", "k", 1_000) { memory } // 서킷 열림
        assertThat(port.consumeCalls).hasSize(1)
        clock.advanceMillis(SharedRateLimiter.DB_RETRY_AFTER_MILLIS + 1) // 만료

        // 시도권을 얻은 요청이 DB 안에서 응답을 기다리는 동안(블랙홀) 다른 요청이 몰려온다
        val hold = port.holdNextConsume()
        val probeResult = AtomicReference<RateLimitTicket?>()
        val probe = Thread { probeResult.set(limiter.tryConsume("s", "probe", 1_000) { memory }) }
        probe.start()
        assertThat(hold.entered.await(5, TimeUnit.SECONDS)).isTrue()
        repeat(20) { assertThat(limiter.tryConsume("s", "k", 1_000) { memory }).isNotNull() }

        assertThat(port.consumeCalls).describedAs("만료 직후 DB 호출은 시도권을 얻은 한 건뿐 (총 2건)").hasSize(2)
        hold.release.countDown()
        probe.join(5_000)
    }

    @Test
    fun `half-open 시도가 성공하면 서킷이 닫히고 이후 요청은 모두 DB 로 간다 (QA F-2)`() {
        port.failing = true
        val memory = bucket(1_000)
        limiter.tryConsume("s", "k", 5) { memory }
        port.failing = false
        clock.advanceMillis(SharedRateLimiter.DB_RETRY_AFTER_MILLIS + 1)

        assertThat(limiter.tryConsume("s", "k", 5, ::noFallback)).isNotNull() // 시도권 요청이 성공 → 닫힘
        repeat(3) { assertThat(limiter.tryConsume("s", "k", 5, ::noFallback)).isNotNull() }

        assertThat(port.usedOf("s", "k")).isEqualTo(4)
    }

    @Test
    fun `half-open 시도가 실패하면 다시 5초 동안 모든 요청이 DB 를 건너뛴다 (QA F-2)`() {
        port.failing = true
        val memory = bucket(1_000)
        limiter.tryConsume("s", "k", 1_000) { memory }
        clock.advanceMillis(SharedRateLimiter.DB_RETRY_AFTER_MILLIS + 1)
        limiter.tryConsume("s", "k", 1_000) { memory } // 시도권 요청 — 실패
        assertThat(port.consumeCalls).hasSize(2)

        repeat(20) { limiter.tryConsume("s", "k", 1_000) { memory } }

        assertThat(port.consumeCalls).hasSize(2)
    }

    @Test
    fun `서킷이 열려 있으면 환불도 DB 를 건너뛴다 - 환불 때문에 DB 대기를 하지 않는다 (QA F-2)`() {
        val ticket = limiter.tryConsume("s", "k", 5, ::noFallback)!!
        port.failing = true
        limiter.tryConsume("s", "other", 5) { bucket(5) } // 실패 → 서킷 열림
        assertThat(port.refundCalls).isEmpty()

        ticket.refund()

        assertThat(port.refundCalls).describedAs("서킷이 열려 있는 동안 DB 환불 호출 없음").isEmpty()
        assertThat(warns).hasSize(1)
    }

    @Test
    fun `서킷이 닫히면 환불은 다시 DB 로 간다 (QA F-2)`() {
        val ticket = limiter.tryConsume("s", "k", 5, ::noFallback)!!
        port.failing = true
        limiter.tryConsume("s", "other", 5) { bucket(5) }
        port.failing = false
        clock.advanceMillis(SharedRateLimiter.DB_RETRY_AFTER_MILLIS + 1)

        ticket.refund()

        assertThat(port.usedOf("s", "k")).isEqualTo(0)
    }

    @Test
    fun `환불은 차감한 날짜의 행에 돌려준다 - 자정을 넘겨도 새 날 행을 건드리지 않는다 (QA F-4)`() {
        val d1 = port.today
        val ticket = limiter.tryConsume("s", "k", 1, ::noFallback)!!
        val d2 = d1.plusDays(1)
        port.today = d2 // 자정이 지났다
        limiter.tryConsume("s", "k", 1, ::noFallback)!! // 새 날 행 used=1

        ticket.refund()

        assertThat(port.refundDates).containsExactly(d1)
        assertThat(port.usedOf("s", "k", d1)).isEqualTo(0)
        assertThat(port.usedOf("s", "k", d2)).isEqualTo(1)
    }

    @Test
    fun `DB 가 돌아오면 다시 DB 로 판정하고 복구 로그를 한 번 남긴다`() {
        port.failing = true
        val memory = bucket(1_000)
        limiter.tryConsume("s", "k", 5) { memory }

        port.failing = false
        clock.advanceMillis(SharedRateLimiter.DB_RETRY_AFTER_MILLIS + 1)
        assertThat(limiter.tryConsume("s", "k", 5, ::noFallback)).isNotNull()
        assertThat(port.usedOf("s", "k")).isEqualTo(1)

        val infos = appender.list.filter { it.level == Level.INFO }
        assertThat(infos).hasSize(1)
        assertThat(warns).hasSize(1)
    }

    @Test
    fun `capacity 가 Int 범위를 넘어도 안전하게 잘라서 넘긴다`() {
        limiter.tryConsume("s", "k", Long.MAX_VALUE, ::noFallback)

        assertThat(port.consumeCalls.single().third).isEqualTo(Int.MAX_VALUE)
    }
}
