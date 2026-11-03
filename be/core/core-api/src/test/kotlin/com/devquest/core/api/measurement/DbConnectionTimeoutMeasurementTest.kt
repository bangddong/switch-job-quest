package com.devquest.core.api.measurement

import com.devquest.storage.db.core.adapter.RateLimitCounterAdapter
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * D-016 DB 타임아웃 결정 전 실측 - M4 (DB 가 조용히 죽었을 때 요청이 얼마나 막히는가).
 *
 * 30초대 대기를 재므로 `measurement` 태그로 기본 `test` 에서 뺐다: `./gradlew :core:core-api:measurementTest`
 * (M4-b 는 Docker 필요 - Postgres Testcontainers).
 *
 * 세 구성(prod 풀 설정 미러: maximumPoolSize=10, minimumIdle=0, connectionTestQuery="SELECT 1",
 * URL `sslmode=require`는 M4-a 만 - 아래 각주):
 *  - current : Hikari 기본(connectionTimeout 30000, validationTimeout 5000) + URL 파라미터 없음
 *  - optB    : URL `connectTimeout=5&socketTimeout=30`, validationTimeout 2000, connectionTimeout 30000
 *  - optA    : optB + connectionTimeout 5000   <- **채택됨(B7-21 ⓐ)**: prod 프로필 core-api·daily-api 가 이 값이다.
 *                (실효 설정은 CoreApiProdDbSettingsTest·DailyApiProdDbSettingsTest 가 지키고, 이 테스트는 "왜 이 값인가"의 실측 근거로 남긴다.
 *                current/optB 는 비교용 구성이며 앱 설정이 아니다.)
 *
 * M4-a 블랙홀: accept 만 하고 아무것도 보내지 않는 서버 -> `getConnection()` 이 예외를 낼 때까지, 그리고
 *       그 풀 위에 올린 [RateLimitCounterAdapter.tryConsume] 이 실패할 때까지.
 * M4-b 동결: 실제 Postgres 앞에 [FreezableProxy] 를 두고 정상 동작 중에 프록시를 얼린다(연결은 열린 채 응답만 끊김).
 *   (i)  유휴 커넥션이 얼었을 때(풀이 빌려주기 전에 검증) tryConsume 이 끝나는 시간
 *   (ii) 이미 빌려준 것처럼 검증 없이 쿼리가 나간 뒤 얼었을 때(= 요청 처리 중 DB 정지) tryConsume 이 끝나는 시간.
 *        어댑터에 queryTimeout 1초가 걸려 있으므로 "1초 안에 끝나야" 하는 호출이다.
 *
 * ⚠️ 각주: M4-b 는 `sslmode=disable` 이다(테스트 컨테이너 Postgres 에 TLS 가 없다). M4-a 는 prod 와 같은 `sslmode=require`.
 */
@Tag("measurement")
class DbConnectionTimeoutMeasurementTest {

    private data class Cfg(
        val name: String,
        val urlParams: String,
        val connectionTimeoutMs: Long,
        val validationTimeoutMs: Long,
    )

    private val configs = listOf(
        Cfg("current", "", 30_000, 5_000),
        Cfg("optB", "&connectTimeout=5&socketTimeout=30", 30_000, 2_000),
        Cfg("optA", "&connectTimeout=5&socketTimeout=30", 5_000, 2_000),
    )

    private fun pool(cfg: Cfg, port: Int, sslmode: String, user: String = "u", password: String = "p", database: String = "x") =
        HikariDataSource(
            HikariConfig().apply {
                poolName = "m4-${cfg.name}-$port"
                jdbcUrl = "jdbc:postgresql://127.0.0.1:$port/$database?sslmode=$sslmode${cfg.urlParams}"
                username = user
                this.password = password
                maximumPoolSize = 10
                minimumIdle = 0
                connectionTestQuery = "SELECT 1"
                connectionTimeout = cfg.connectionTimeoutMs
                validationTimeout = cfg.validationTimeoutMs
                // 기동 실패(fail-fast)로 측정이 시작도 못 하는 것을 막는다 - 풀은 만들고, 첫 getConnection 에서 겪게 한다.
                initializationFailTimeout = -1
            }
        )

    private data class Timed(val seconds: Double?, val outcome: String)

    private inline fun timed(capSeconds: Long, crossinline body: () -> Unit): Timed {
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "m4-call").apply { isDaemon = true } }
        val start = System.nanoTime()
        val future = executor.submit(Callable { body() })
        return try {
            future.get(capSeconds, TimeUnit.SECONDS)
            Timed((System.nanoTime() - start) / 1e9, "returned normally")
        } catch (e: TimeoutException) {
            future.cancel(true)
            Timed(null, ">${capSeconds}s still blocked")
        } catch (e: Exception) {
            val cause = e.cause ?: e
            var root: Throwable = cause
            while (root.cause != null && root.cause !== root) root = root.cause!!
            Timed(
                (System.nanoTime() - start) / 1e9,
                "${cause.javaClass.simpleName} <- root ${root.javaClass.simpleName}: ${root.message?.take(80)}",
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun fmt(t: Timed) = "${t.seconds?.let { "%.1fs".format(it) } ?: "n/a"} [${t.outcome}]"

    /** 세 구성을 동시에(구성별 별도 스레드) 돌려 벽시계 시간을 줄인다. 구성끼리는 풀·서버가 모두 분리돼 있다. */
    private fun <T> parallelPerConfig(body: (Cfg) -> T): Map<String, T> {
        val executor = Executors.newFixedThreadPool(configs.size)
        try {
            return configs.map { cfg -> cfg.name to executor.submit(Callable { body(cfg) }) }
                .associate { (name, f) -> name to f.get(5, TimeUnit.MINUTES) }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M4-a 블랙홀 서버 - getConnection 과 레이트 리밋 tryConsume 이 실패하기까지`() {
        val results = parallelPerConfig { cfg ->
            FreezableProxy(upstream = null).use { blackhole ->
                pool(cfg, blackhole.port, sslmode = "require").use { ds ->
                    val getConnection = timed(60) { ds.connection.close() }
                    // 같은 풀에서 한 번 더 - 레이트 리밋 어댑터(JdbcTemplate, queryTimeout 1초)가 실패하기까지.
                    val rateLimit = timed(60) { RateLimitCounterAdapter(ds).tryConsume("m4", "k", 5) }
                    println("[MEASURE] M4-a blackhole config=${cfg.name} getConnection=${fmt(getConnection)} tryConsume=${fmt(rateLimit)}")
                    getConnection to rateLimit
                }
            }
        }

        // 사실 단언: connectionTimeout 이 대기 시간을 정한다(pgjdbc connect/socketTimeout 은 TCP 가 붙은 뒤의 침묵을 끊지 못한다).
        val cur = results.getValue("current").first
        val b = results.getValue("optB").first
        val a = results.getValue("optA").first
        assertThat(cur.seconds).describedAs("current: ${cur.outcome}").isBetween(29.0, 33.0)
        assertThat(b.seconds).describedAs("optB: ${b.outcome}").isBetween(29.0, 33.0)
        assertThat(a.seconds).describedAs("optA: ${a.outcome}").isBetween(4.5, 8.0)
    }

    @Test
    fun `M4-b 실제 Postgres 앞 프록시를 얼리면 - 유휴 커넥션 검증 경로와 쿼리 진행 중 경로`() {
        val pg = PostgresMeasurementSupport.postgres
        println("[MEASURE] M4-b pg host=${pg.host} port=${pg.firstMappedPort}")

        val results = parallelPerConfig { cfg ->
            // (i) 유휴 커넥션이 얼었다 - 풀이 빌려주기 전에 검증(SELECT 1, validationTimeout)한다.
            val idle = FreezableProxy(upstream = pg.host to pg.firstMappedPort).use { proxy ->
                pool(cfg, proxy.port, "disable", pg.username, pg.password, pg.databaseName).use { ds ->
                    val limiter = RateLimitCounterAdapter(ds)
                    assertThat(limiter.tryConsume("m4-idle-${cfg.name}", "k", 100)).describedAs("얼리기 전 정상 동작").isNotNull()
                    Thread.sleep(700) // Hikari aliveBypassWindow(500ms) 를 넘겨 다음 borrow 에서 검증이 돌게 한다
                    proxy.frozen = true
                    val t = timed(70) { limiter.tryConsume("m4-idle-${cfg.name}", "k", 100) }
                    println("[MEASURE] M4-b(i) idle-frozen config=${cfg.name} tryConsume=${fmt(t)}")
                    t
                }
            }
            // (ii) 방금 쓴 커넥션(500ms 이내 재사용 -> 검증 생략)으로 쿼리가 나간 직후 DB 가 얼었다.
            val inflight = FreezableProxy(upstream = pg.host to pg.firstMappedPort).use { proxy ->
                pool(cfg, proxy.port, "disable", pg.username, pg.password, pg.databaseName).use { ds ->
                    val limiter = RateLimitCounterAdapter(ds)
                    assertThat(limiter.tryConsume("m4-inflight-${cfg.name}", "k", 100)).describedAs("얼리기 전 정상 동작").isNotNull()
                    proxy.frozen = true
                    val t = timed(70) { limiter.tryConsume("m4-inflight-${cfg.name}", "k", 100) }
                    println("[MEASURE] M4-b(ii) inflight-frozen config=${cfg.name} tryConsume=${fmt(t)}")
                    t
                }
            }
            idle to inflight
        }

        // 사실 단언(실측값). (i) 유휴 커넥션이 얼면 connectionTimeout 이 상한이다(검증 실패 -> 새 연결 핸드셰이크도 침묵).
        val idleCur = results.getValue("current").first
        val idleB = results.getValue("optB").first
        val idleA = results.getValue("optA").first
        assertThat(idleCur.seconds).describedAs("(i) current: ${idleCur.outcome}").isBetween(29.0, 33.0)
        assertThat(idleB.seconds).describedAs("(i) optB: ${idleB.outcome}").isBetween(29.0, 33.0)
        assertThat(idleA.seconds).describedAs("(i) optA: ${idleA.outcome}").isBetween(4.5, 8.0)

        // (ii) 쿼리 진행 중 얼면 어댑터의 queryTimeout(1초)이 듣지 않는다. 상한은 pgjdbc socketTimeout 뿐이고,
        // 그게 없으면(current) 측정 상한(70초)까지도 안 풀린다. connectionTimeout 은 이 경로와 무관하다(optA 도 30초).
        val inCur = results.getValue("current").second
        val inB = results.getValue("optB").second
        val inA = results.getValue("optA").second
        assertThat(inCur.seconds).describedAs("(ii) current: ${inCur.outcome}").isNull()
        assertThat(inB.seconds).describedAs("(ii) optB: ${inB.outcome}").isBetween(29.0, 33.0)
        assertThat(inA.seconds).describedAs("(ii) optA: ${inA.outcome}").isBetween(29.0, 33.0)
    }
}
