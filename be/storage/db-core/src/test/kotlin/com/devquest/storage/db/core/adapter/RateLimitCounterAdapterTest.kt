package com.devquest.storage.db.core.adapter

import org.assertj.core.api.Assertions.assertThat
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.sql.DataSource

/**
 * [RateLimitCounterAdapter] 의 SQL 을 H2 에서 검증한다. 어댑터 SQL 은 Postgres·H2 공통 문법이라
 * 같은 쿼리가 여기서도 그대로 돈다 — V14 마이그레이션 파일을 그대로 적용한다.
 *
 * 동시성·실제 Postgres 동작은 core-api 의 Testcontainers 테스트가 맡는다.
 */
class RateLimitCounterAdapterTest {

    private val kst = ZoneId.of("Asia/Seoul")
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:rate-limit-adapter;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbc = JdbcTemplate(dataSource)

    private fun adapterAt(isoInstant: String) =
        RateLimitCounterAdapter(dataSource, Clock.fixed(Instant.parse(isoInstant), ZoneOffset.UTC))

    // 2026-10-29 10:00 KST
    private val today = "2026-10-29T01:00:00Z"
    private val todayDate = LocalDate.of(2026, 10, 29)

    @BeforeEach
    fun createFreshTable() {
        jdbc.execute("DROP TABLE IF EXISTS rate_limit_counter")
        ResourceDatabasePopulator(ClassPathResource("db/migration/V14__create_rate_limit_counter.sql")).execute(dataSource)
    }

    private fun usedOf(scope: String, key: String): Int? =
        jdbc.queryForList(
            "SELECT used FROM rate_limit_counter WHERE scope = ? AND limit_key = ?",
            Int::class.java,
            scope,
            RateLimitCounterAdapter.hash(key),
        ).firstOrNull()

    @Test
    fun `상한 미만이면 차감되고 상한에 닿으면 거부되며 거부는 사용량을 늘리지 않는다`() {
        val adapter = adapterAt(today)

        assertThat(adapter.tryConsume("s", "k", 2)).isNotNull()
        assertThat(adapter.tryConsume("s", "k", 2)).isNotNull()
        assertThat(adapter.tryConsume("s", "k", 2)).isNull()
        assertThat(adapter.tryConsume("s", "k", 2)).isNull()

        assertThat(usedOf("s", "k")).isEqualTo(2)
    }

    @Test
    fun `용량 1 이면 첫 요청만 통과한다`() {
        val adapter = adapterAt(today)

        assertThat(adapter.tryConsume("s", "k", 1)).isNotNull()
        assertThat(adapter.tryConsume("s", "k", 1)).isNull()
    }

    @Test
    fun `용량 0 이하면 행을 만들지 않고 거부한다`() {
        val adapter = adapterAt(today)

        assertThat(adapter.tryConsume("s", "k", 0)).isNull()

        assertThat(jdbc.queryForObject("SELECT count(*) FROM rate_limit_counter", Int::class.java)).isEqualTo(0)
    }

    @Test
    fun `키가 다르면 서로의 한도에 영향을 주지 않는다`() {
        val adapter = adapterAt(today)
        assertThat(adapter.tryConsume("s", "a", 1)).isNotNull()

        assertThat(adapter.tryConsume("s", "a", 1)).isNull()
        assertThat(adapter.tryConsume("s", "b", 1)).isNotNull()
    }

    @Test
    fun `scope 가 다르면 같은 키도 따로 센다`() {
        val adapter = adapterAt(today)
        assertThat(adapter.tryConsume("explain", "k", 1)).isNotNull()

        assertThat(adapter.tryConsume("explain", "k", 1)).isNull()
        assertThat(adapter.tryConsume("evaluate", "k", 1)).isNotNull()
    }

    @Test
    fun `날짜가 바뀌면 새 행에서 다시 센다 - 자정 리셋 없이`() {
        assertThat(adapterAt(today).tryConsume("s", "k", 1)).isNotNull()
        assertThat(adapterAt(today).tryConsume("s", "k", 1)).isNull()

        val tomorrow = "2026-10-30T01:00:00Z"
        assertThat(adapterAt(tomorrow).tryConsume("s", "k", 1)).isNotNull()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rate_limit_counter", Int::class.java)).isEqualTo(2)
    }

    @Test
    fun `날짜 경계는 UTC 가 아니라 KST 자정이다`() {
        // 2026-10-29 14:59 UTC = 2026-10-29 23:59 KST, 15:00 UTC = 2026-10-30 00:00 KST
        assertThat(adapterAt("2026-10-29T14:59:00Z").tryConsume("s", "k", 1)).isNotNull()
        assertThat(adapterAt("2026-10-29T14:59:30Z").tryConsume("s", "k", 1)).isNull()
        assertThat(adapterAt("2026-10-29T15:00:00Z").tryConsume("s", "k", 1)).isNotNull()

        val dates = jdbc.queryForList("SELECT CAST(limit_date AS VARCHAR(10)) FROM rate_limit_counter ORDER BY limit_date", String::class.java)
        assertThat(dates).containsExactly("2026-10-29", "2026-10-30")
    }

    @Test
    fun `환불하면 사용량이 1 줄어 다시 차감할 수 있다`() {
        val adapter = adapterAt(today)
        assertThat(adapter.tryConsume("s", "k", 1)).isNotNull()
        assertThat(adapter.tryConsume("s", "k", 1)).isNull()

        adapter.refund("s", "k", todayDate)

        assertThat(usedOf("s", "k")).isEqualTo(0)
        assertThat(adapter.tryConsume("s", "k", 1)).isNotNull()
    }

    private fun usedOn(scope: String, key: String, date: LocalDate): Int? =
        jdbc.queryForList(
            "SELECT used FROM rate_limit_counter WHERE scope = ? AND limit_key = ? AND limit_date = ?",
            Int::class.java,
            scope,
            RateLimitCounterAdapter.hash(key),
            java.sql.Date.valueOf(date),
        ).firstOrNull()

    @Test
    fun `차감에 성공하면 차감한 KST 날짜를 돌려주고 거부하면 null 이다`() {
        assertThat(adapterAt("2026-10-29T14:59:00Z").tryConsume("s", "k", 1)).isEqualTo(LocalDate.of(2026, 10, 29)) // 23:59 KST
        assertThat(adapterAt("2026-10-29T14:59:30Z").tryConsume("s", "k", 1)).isNull()
        assertThat(adapterAt("2026-10-29T15:00:00Z").tryConsume("s", "k", 1)).isEqualTo(LocalDate.of(2026, 10, 30)) // 00:00 KST 다음 날
    }

    @Test
    fun `자정을 낀 환불은 차감한 날짜의 행에 돌려주고 새 날짜 행은 건드리지 않는다 (QA F-4)`() {
        val d1 = requireNotNull(adapterAt("2026-10-29T14:59:00Z").tryConsume("s", "k", 1)) // 23:59 KST 차감
        val after = adapterAt("2026-10-29T15:01:00Z") // 00:01 KST — 새 날
        val d2 = requireNotNull(after.tryConsume("s", "k", 1)) // 새 날 행 used=1

        after.refund("s", "k", d1) // 차감 시점의 날짜로 환불

        assertThat(usedOn("s", "k", d1)).isEqualTo(0)
        assertThat(usedOn("s", "k", d2)).describedAs("새 날 행은 그대로").isEqualTo(1)
    }

    @Test
    fun `updated_at 은 INSERT·증가·환불 모두 DB 시각(NOW) 기준이라 DDL 기본값과 같은 시계다 (QA F-4)`() {
        // 앱 시계는 2026-10-29 10:00 KST 로 고정 — 앱이 시각을 바인딩하면 실제 DB 시각과 크게 어긋난다.
        val adapter = adapterAt(today)
        fun updatedAtGap(): Duration {
            val updated = jdbc.queryForObject("SELECT updated_at FROM rate_limit_counter", LocalDateTime::class.java)
            val dbNow = jdbc.queryForObject("SELECT CAST(NOW() AS TIMESTAMP)", LocalDateTime::class.java)
            return Duration.between(updated, dbNow).abs()
        }

        adapter.tryConsume("s", "k", 3) // INSERT 경로
        assertThat(updatedAtGap()).describedAs("INSERT").isLessThan(Duration.ofMinutes(1))
        jdbc.update("UPDATE rate_limit_counter SET updated_at = TIMESTAMP '2000-01-01 00:00:00'")
        adapter.tryConsume("s", "k", 3) // UPDATE 경로
        assertThat(updatedAtGap()).describedAs("증가").isLessThan(Duration.ofMinutes(1))
        jdbc.update("UPDATE rate_limit_counter SET updated_at = TIMESTAMP '2000-01-01 00:00:00'")
        adapter.refund("s", "k", todayDate)
        assertThat(updatedAtGap()).describedAs("환불").isLessThan(Duration.ofMinutes(1))
    }

    @Test
    fun `환불은 0 아래로 내려가지 않고 행이 없어도 에러가 아니다`() {
        val adapter = adapterAt(today)

        adapter.refund("s", "never-used", todayDate)
        assertThat(usedOf("s", "never-used")).isNull()

        adapter.tryConsume("s", "k", 3)
        repeat(3) { adapter.refund("s", "k", todayDate) }
        assertThat(usedOf("s", "k")).isEqualTo(0)
    }

    @Test
    fun `키는 SHA-256 hex 로만 저장된다 - 원문 IP 가 DB 에 남지 않는다`() {
        val adapter = adapterAt(today)
        adapter.tryConsume("s", "203.0.113.7", 1)

        val stored = jdbc.queryForList("SELECT limit_key FROM rate_limit_counter", String::class.java)

        assertThat(stored).hasSize(1)
        assertThat(stored.single()).hasSize(64).matches("[0-9a-f]{64}").isNotEqualTo("203.0.113.7")
        // 알려진 SHA-256("abc")
        assertThat(RateLimitCounterAdapter.hash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun `긴 키도 64자 해시로 고정된다`() {
        val adapter = adapterAt(today)

        assertThat(adapter.tryConsume("s", "x".repeat(5_000), 1)).isNotNull()
        assertThat(adapter.tryConsume("s", "x".repeat(5_000), 1)).isNull()
    }

    @Test
    fun `보관 기간 2일이 지난 행만 지운다 - 멱등`() {
        val old = RateLimitCounterAdapter.hash("old")
        fun insert(date: String, key: String) = jdbc.update(
            "INSERT INTO rate_limit_counter (scope, limit_key, limit_date, used, updated_at) VALUES ('s', ?, CAST(? AS DATE), 1, CURRENT_TIMESTAMP)",
            key, date,
        )
        insert("2026-10-25", old) // today - 4
        insert("2026-10-26", RateLimitCounterAdapter.hash("d3")) // today - 3 → 삭제
        insert("2026-10-27", RateLimitCounterAdapter.hash("d2")) // today - 2 → 보관
        insert("2026-10-28", RateLimitCounterAdapter.hash("d1"))
        insert("2026-10-29", RateLimitCounterAdapter.hash("d0"))
        val adapter = adapterAt(today)

        assertThat(adapter.deleteExpired()).isEqualTo(2)
        assertThat(adapter.deleteExpired()).isEqualTo(0)

        val remaining = jdbc.queryForList("SELECT CAST(limit_date AS VARCHAR(10)) FROM rate_limit_counter ORDER BY limit_date", String::class.java)
        assertThat(remaining).containsExactly("2026-10-27", "2026-10-28", "2026-10-29")
    }

    @Test
    fun `같은 시각에 다른 앱이 먼저 행을 만들어 둬도 차감이 이어진다`() {
        // 행이 이미 있는 상태에서 첫 호출이 UPDATE 경로로 가는지 — INSERT 경합 복구는 core-api 동시성 테스트가 다룬다
        val adapter = adapterAt(today)
        val other = adapterAt(today)

        assertThat(adapter.tryConsume("s", "k", 3)).isNotNull()
        assertThat(other.tryConsume("s", "k", 3)).isNotNull()
        assertThat(adapter.tryConsume("s", "k", 3)).isNotNull()
        assertThat(other.tryConsume("s", "k", 3)).isNull()
    }

    /**
     * 첫 INSERT 문장을 준비하는 순간(= UPDATE 가 0행이었던 직후) [beforeFirstInsert] 를 끼워 넣는 DataSource.
     * 타 요청이 그 사이에 행을 만들고 커밋한 경합을 스레드 타이밍 없이 결정적으로 재현한다.
     */
    private fun dataSourceInterceptingFirstInsert(beforeFirstInsert: () -> Unit): DataSource {
        var fired = false
        fun wrap(connection: Connection): Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader, arrayOf(Connection::class.java),
        ) { _, method, args ->
            val sql = args?.firstOrNull() as? String
            if (!fired && method.name == "prepareStatement" && sql != null && sql.startsWith("INSERT INTO rate_limit_counter")) {
                fired = true
                beforeFirstInsert()
            }
            try {
                method.invoke(connection, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        } as Connection
        return object : DataSource by dataSource {
            override fun getConnection(): Connection = wrap(dataSource.connection)
            override fun getConnection(username: String, password: String): Connection = wrap(dataSource.getConnection(username, password))
        }
    }

    private fun insertRowFromOtherRequest(key: String, used: Int) {
        jdbc.update(
            "INSERT INTO rate_limit_counter (scope, limit_key, limit_date, used, updated_at) VALUES ('s', ?, CAST('2026-10-29' AS DATE), ?, CURRENT_TIMESTAMP)",
            RateLimitCounterAdapter.hash(key), used,
        )
    }

    @Test
    fun `첫 사용 경합 - UPDATE 0행 직후 타 요청이 행을 만들어 INSERT 가 0행이어도 상한 미만이면 허용한다 (QA F-1)`() {
        // 재현: A ①UPDATE 0행 → B 가 INSERT 커밋(used=1) → A ②INSERT…NOT EXISTS 0행. cap=5 이므로 A 는 허용되어야 한다.
        val adapter = RateLimitCounterAdapter(
            dataSourceInterceptingFirstInsert { insertRowFromOtherRequest("k", 1) },
            Clock.fixed(Instant.parse(today), ZoneOffset.UTC),
        )

        assertThat(adapter.tryConsume("s", "k", 5)).isNotNull()

        assertThat(usedOf("s", "k")).isEqualTo(2)
    }

    @Test
    fun `첫 사용 경합 - 타 요청이 먼저 상한을 채웠으면 거부하고 사용량을 늘리지 않는다 (QA F-1)`() {
        val adapter = RateLimitCounterAdapter(
            dataSourceInterceptingFirstInsert { insertRowFromOtherRequest("k", 1) },
            Clock.fixed(Instant.parse(today), ZoneOffset.UTC),
        )

        assertThat(adapter.tryConsume("s", "k", 1)).isNull()

        assertThat(usedOf("s", "k")).isEqualTo(1)
    }

    @Test
    fun `테이블이 없으면 예외를 던진다 - 호출처가 대체 경로로 전환할 수 있게`() {
        jdbc.execute("DROP TABLE rate_limit_counter")

        org.junit.jupiter.api.assertThrows<org.springframework.dao.DataAccessException> {
            adapterAt(today).tryConsume("s", "k", 1)
        }
    }

    @Test
    fun `커넥션을 문장마다 즉시 반납한다 - 풀 크기 1 이어도 연속 호출이 막히지 않고 사용 중 커넥션이 0 이다`() {
        // 인터셉터 호출 직후 요청은 최대 150초짜리 AI 호출로 이어진다. 커넥션을 쥐고 있으면 풀이 마른다(OSIV 함정).
        val pool = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:rate-limit-adapter;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = 1
            connectionTimeout = 500
        })
        pool.use {
            val adapter = RateLimitCounterAdapter(it, Clock.fixed(Instant.parse(today), ZoneOffset.UTC))

            repeat(5) { i -> adapter.tryConsume("s", "k$i", 1) }
            adapter.refund("s", "k0", todayDate)
            adapter.deleteExpired()

            assertThat(it.hikariPoolMXBean.activeConnections).isEqualTo(0)
        }
    }
}
