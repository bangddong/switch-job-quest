package com.devquest.daily.support

import com.devquest.core.domain.port.RateLimitCounterPort
import io.github.bucket4j.Bucket
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 공유 DB 카운터([RateLimitCounterPort])로 한도를 판정하고, DB 가 안 되면 **인스턴스 메모리 버킷으로 대체**한다(fail-soft).
 * core-api 의 같은 이름 클래스와 동일 규칙이다(D-007 복제 — core-api 를 의존할 수 없어 daily-api 자체 소유로 둔다).
 *
 * 왜 fail-closed(DB 장애 = 전원 거부)가 아닌가: Fly 는 DB 없이도 AI 경로가 돈다. 레이트 리밋 때문에 DB 장애가
 * 서비스 장애로 번지면 지금(메모리 버킷)보다 나쁘다. 대체 구간에는 한도가 인스턴스별로 따로 돌지만 전체 일일 상한도
 * 같은 방식으로 대체되고, 그 구간은 DB 가 죽은 동안뿐이다.
 *
 * ## 서킷(짧은 우회, half-open)
 * DB 가 느리게 죽으면(연결 풀 대기·연결 타임아웃) 요청마다 DB 시도를 기다려 fail-soft 의 의미가 사라진다.
 * 실패하면 [DB_RETRY_AFTER_MILLIS] 동안은 DB 를 건너뛰고 바로 메모리로 간다. 그 시간이 지나면 **단 한 요청만**
 * (CAS 로 시도권을 얻은 요청) DB 를 시도하고, 그 요청이 끝날 때까지 나머지는 계속 메모리로 간다.
 * 시도가 성공하면 서킷이 닫히고, 실패하면 다시 [DB_RETRY_AFTER_MILLIS] 동안 닫힌다.
 * 시도 요청이 응답 없이 매달려도(블랙홀) 시도권은 [PROBE_LEASE_MILLIS] 뒤에 만료되어 다른 요청이 이어받는다.
 *
 * ## 환불
 * 환불([RateLimitTicket.refund])도 같은 서킷을 따른다 — 서킷이 열려 있으면 DB 를 건너뛴다(환불 때문에 요청이 DB 를 기다리지 않는다).
 * 🔴 이때 **DB 에서 차감했던 토큰의 환불은 유실**된다. 개인 한도는 그 하루 한 건이 더 소진된 채 남는다(과소 허용 방향).
 * 환불은 전체 상한에 막힌 요청에서만 일어나 드물고, 보류했다 재시도하는 큐는 서킷보다 복잡해서 두지 않는다.
 * 환불은 차감한 날짜([RateLimitCounterPort.tryConsume] 반환값)의 행으로 가므로 자정을 넘겨도 새 날 행을 건드리지 않는다.
 *
 * ## 로그
 * WARN 은 정상→장애로 **바뀔 때 한 번**, INFO 는 복구될 때 한 번이다. 요청마다 남기지 않는다(장애 중 로그 폭주 방지).
 */
@Component
class SharedRateLimiter internal constructor(
    private val counterPort: RateLimitCounterPort,
    private val clock: Clock,
) {

    @Autowired
    constructor(counterPort: RateLimitCounterPort) : this(counterPort, Clock.systemUTC())

    private val log = LoggerFactory.getLogger(javaClass)
    private val degraded = AtomicBoolean(false)

    /**
     * 서킷이 열려 있을 때(degraded) 다음 DB 시도가 허용되는 시각. 시도권을 얻은 요청이 이 값을 [PROBE_LEASE_MILLIS] 뒤로
     * 밀어 두고(= 다른 요청은 건너뜀), 결과에 따라 성공이면 서킷이 닫히고 실패면 [DB_RETRY_AFTER_MILLIS] 뒤로 다시 잡힌다.
     */
    private val nextDbAttemptMillis = AtomicLong(0L)

    /**
     * 키 하나에 대해 하루 [capacity] 회 한도로 1회 차감을 시도한다.
     *
     * @param fallback DB 를 쓸 수 없을 때만 호출된다 — 정상일 땐 메모리 버킷을 만들지 않는다(키 폭주 시 메모리 보호).
     * @return 차감 성공 시 환불용 [RateLimitTicket], 한도 초과면 null
     */
    fun tryConsume(scope: String, key: String, capacity: Long, fallback: () -> Bucket): RateLimitTicket? {
        if (acquireDbAttempt()) {
            try {
                val date = counterPort.tryConsume(scope, key, capacity.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
                markHealthy()
                return if (date != null) RateLimitTicket { refundDb(scope, key, date) } else null
            } catch (e: Exception) {
                markFailed(e)
            }
        }
        val bucket = fallback()
        return if (bucket.tryConsume(1)) RateLimitTicket { bucket.addTokens(1) } else null
    }

    private fun refundDb(scope: String, key: String, date: LocalDate) {
        if (!acquireDbAttempt()) {
            // 서킷이 열려 있다 — 환불을 위해 DB 를 기다리지 않는다. 이 토큰은 유실된다(클래스 KDoc 참조).
            log.info("레이트 리밋 공유 카운터(DB) 서킷이 열려 있어 환불을 건너뜁니다: scope={}, date={}", scope, date)
            return
        }
        try {
            counterPort.refund(scope, key, date)
            markHealthy()
        } catch (e: Exception) {
            // 환불 실패로 요청을 500 으로 만들지 않는다 — 토큰 하나를 잃을 뿐이다.
            markFailed(e)
        }
    }

    /**
     * 이 요청이 DB 를 시도해도 되는가. 서킷이 닫혀 있으면 항상 true, 열려 있으면 대기 시간이 지난 뒤
     * CAS 에 이긴 **한 요청만** true(half-open)다.
     */
    private fun acquireDbAttempt(): Boolean {
        if (!degraded.get()) return true
        val now = clock.millis()
        val next = nextDbAttemptMillis.get()
        if (now < next) return false
        return nextDbAttemptMillis.compareAndSet(next, now + PROBE_LEASE_MILLIS)
    }

    private fun markHealthy() {
        if (degraded.compareAndSet(true, false)) {
            log.info("레이트 리밋 공유 카운터(DB)가 복구되어 다시 DB 로 판정합니다.")
        }
    }

    private fun markFailed(e: Exception) {
        // 시도권 시각을 먼저 정한 뒤 열림 표시를 한다 — 열림을 본 요청이 항상 새 시각을 본다.
        nextDbAttemptMillis.set(clock.millis() + DB_RETRY_AFTER_MILLIS)
        if (degraded.compareAndSet(false, true)) {
            log.warn(
                "레이트 리밋 공유 카운터(DB) 사용 불가 — 인스턴스 메모리 버킷으로 대체합니다(복구 시 한 번 알립니다): {}: {}",
                e.javaClass.simpleName, e.message,
            )
        }
    }

    companion object {
        /** DB 실패 후 DB 를 건너뛰는 시간. */
        const val DB_RETRY_AFTER_MILLIS = 5_000L

        /** half-open 시도 요청이 결과 없이 매달렸을 때 다른 요청이 시도권을 이어받기까지의 시간(Hikari 연결 대기 30초보다 길게). */
        const val PROBE_LEASE_MILLIS = 60_000L
    }
}

/** 한도 차감 1회의 영수증 — 이후 단계(전체 상한)에서 막히면 [refund] 로 되돌린다. 차감한 저장소(DB·메모리)로 돌아간다. */
class RateLimitTicket(private val refundAction: () -> Unit) {
    fun refund() = refundAction()
}
