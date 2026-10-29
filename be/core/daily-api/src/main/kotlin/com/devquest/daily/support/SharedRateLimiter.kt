package com.devquest.daily.support

import com.devquest.core.domain.port.RateLimitCounterPort
import io.github.bucket4j.Bucket
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 공유 DB 카운터([RateLimitCounterPort])로 한도를 판정하고, DB 가 안 되면 **인스턴스 메모리 버킷으로 대체**한다(fail-soft).
 * core-api 의 같은 이름 클래스와 동일 규칙이다(D-007 복제 — core-api 를 의존할 수 없어 daily-api 자체 소유로 둔다).
 *
 * 왜 fail-closed(DB 장애 = 전원 거부)가 아닌가: Fly 는 DB 없이도 AI 경로가 돈다. 레이트 리밋 때문에 DB 장애가
 * 서비스 장애로 번지면 지금(메모리 버킷)보다 나쁘다. 대체 구간에는 한도가 인스턴스별로 따로 돌지만 전체 일일 상한도
 * 같은 방식으로 대체되고, 그 구간은 DB 가 죽은 동안뿐이다.
 *
 * ## 서킷(짧은 우회)
 * DB 가 느리게 죽으면(연결 풀 대기·연결 타임아웃) 요청마다 DB 시도를 기다려 fail-soft 의 의미가 사라진다.
 * 실패하면 [DB_RETRY_AFTER_MILLIS] 동안은 DB 를 건너뛰고 바로 메모리로 가고, 그 뒤 한 번 다시 시도한다.
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

    @Volatile
    private var skipDbUntilMillis = 0L

    /**
     * 키 하나에 대해 하루 [capacity] 회 한도로 1회 차감을 시도한다.
     *
     * @param fallback DB 를 쓸 수 없을 때만 호출된다 — 정상일 땐 메모리 버킷을 만들지 않는다(키 폭주 시 메모리 보호).
     * @return 차감 성공 시 환불용 [RateLimitTicket], 한도 초과면 null
     */
    fun tryConsume(scope: String, key: String, capacity: Long, fallback: () -> Bucket): RateLimitTicket? {
        if (dbAvailable()) {
            try {
                val consumed = counterPort.tryConsume(scope, key, capacity.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
                markHealthy()
                return if (consumed) RateLimitTicket { refundDb(scope, key) } else null
            } catch (e: Exception) {
                markFailed(e)
            }
        }
        val bucket = fallback()
        return if (bucket.tryConsume(1)) RateLimitTicket { bucket.addTokens(1) } else null
    }

    private fun refundDb(scope: String, key: String) {
        try {
            counterPort.refund(scope, key)
        } catch (e: Exception) {
            // 환불 실패로 요청을 500 으로 만들지 않는다 — 토큰 하나를 잃을 뿐이다.
            markFailed(e)
        }
    }

    private fun dbAvailable(): Boolean = clock.millis() >= skipDbUntilMillis

    private fun markHealthy() {
        if (degraded.compareAndSet(true, false)) {
            log.info("레이트 리밋 공유 카운터(DB)가 복구되어 다시 DB 로 판정합니다.")
        }
    }

    private fun markFailed(e: Exception) {
        skipDbUntilMillis = clock.millis() + DB_RETRY_AFTER_MILLIS
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
    }
}

/** 한도 차감 1회의 영수증 — 이후 단계(전체 상한)에서 막히면 [refund] 로 되돌린다. 차감한 저장소(DB·메모리)로 돌아간다. */
class RateLimitTicket(private val refundAction: () -> Unit) {
    fun refund() = refundAction()
}
