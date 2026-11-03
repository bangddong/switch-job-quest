package com.devquest.core.api.measurement

import com.zaxxer.hikari.HikariDataSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * 측정 전용 도구(prod 코드 아님) — 외부 경계(LLM·Judge0 포트)를 테스트 더블에서 "블록"시킨 채
 * HikariCP 풀의 active 커넥션 수를 읽는다.
 *
 * 쓰는 법: 포트 더블의 answer 안에서 [BlockingGate.block] 을 호출하면, 요청 스레드가 그 지점에서
 * 멈추고 [BlockingGate.entered] 가 열린다. 측정 스레드는 그 사이에 [PoolSnapshot] 을 읽고
 * [BlockingGate.release] 로 요청을 이어가게 한다.
 */
class BlockingGate(private val maxWaitSeconds: Long = 30) {
    val entered = CountDownLatch(1)
    private val released = CountDownLatch(1)

    /** 요청 스레드(테스트 더블 안)에서 호출. 측정이 끝나 [release] 되거나 [maxWaitSeconds] 가 지나면 돌아간다. */
    fun block() {
        entered.countDown()
        released.await(maxWaitSeconds, TimeUnit.SECONDS)
    }

    fun release() = released.countDown()
}

data class PoolSnapshot(val active: Int, val idle: Int, val total: Int, val awaiting: Int) {
    override fun toString() = "active=$active idle=$idle total=$total awaiting=$awaiting"
}

class HikariProbe(dataSource: DataSource) {
    private val hikari: HikariDataSource = dataSource.unwrap(HikariDataSource::class.java)

    val maximumPoolSize: Int get() = hikari.maximumPoolSize

    fun snapshot(): PoolSnapshot {
        val mx = hikari.hikariPoolMXBean
        return PoolSnapshot(mx.activeConnections, mx.idleConnections, mx.totalConnections, mx.threadsAwaitingConnection)
    }
}

data class BlockedMeasurement<T>(val samples: List<PoolSnapshot>, val result: T) {
    val minActive: Int get() = samples.minOf { it.active }
    val maxActive: Int get() = samples.maxOf { it.active }
}

/**
 * [request] 를 별도 스레드에서 실행하고, [gate] 에 진입(=외부 경계에서 블록)할 때까지 기다린 뒤
 * 풀 스냅샷을 [sampleCount] 번 읽고(간격 [sampleIntervalMs]) 요청을 이어가게 해서 결과를 돌려준다.
 * 게이트에 진입하지 못하면(요청이 그 전에 끝났거나 실패하면) 실패시킨다 — "측정했는데 블록 지점을 안 지났다" 방지.
 */
fun <T> measureWhileBlocked(
    probe: HikariProbe,
    gate: BlockingGate,
    sampleCount: Int = 5,
    sampleIntervalMs: Long = 40,
    request: () -> T,
): BlockedMeasurement<T> {
    val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "measure-request").apply { isDaemon = true } }
    try {
        val future = executor.submit<T> { request() }
        val reached = gate.entered.await(15, TimeUnit.SECONDS)
        if (!reached) {
            // 요청이 게이트 전에 끝났거나 죽었다 — 원인을 드러낸다.
            gate.release()
            val outcome = runCatching { future.get(5, TimeUnit.SECONDS) }
            throw AssertionError("요청이 블록 지점에 도달하지 못했다: $outcome")
        }
        val samples = ArrayList<PoolSnapshot>(sampleCount)
        repeat(sampleCount) {
            samples += probe.snapshot()
            Thread.sleep(sampleIntervalMs)
        }
        gate.release()
        val result = future.get(30, TimeUnit.SECONDS)
        return BlockedMeasurement(samples, result)
    } finally {
        gate.release()
        executor.shutdownNow()
    }
}
