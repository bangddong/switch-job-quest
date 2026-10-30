package com.devquest.core.api.support

import com.devquest.storage.db.core.RateLimitCounterEntity
import com.devquest.storage.db.core.adapter.RateLimitCounterAdapter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * U-6 — 공유 레이트 리밋 카운터 SQL 을 **실제 Postgres**(운영과 같은 17.10)에서 검증한다.
 * H2 테스트(db-core `RateLimitCounterAdapterTest`)는 문법 호환까지만 증명하고, 원자성·행 잠금·타임아웃은 여기서 증명한다.
 *
 * 스키마는 운영과 같이 Flyway(V1~V14)로 올린다. 도커가 없으면 조용히 스킵하지 않고 실패한다(FlywayMigrationIntegrationTest 와 같은 이유).
 */
class RateLimitCounterPostgresTest {

    companion object {
        private const val POSTGRES_IMAGE = "postgres:17.10"

        private val postgres: PostgreSQLContainer = try {
            PostgreSQLContainer(POSTGRES_IMAGE).apply { start() }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Testcontainers가 Postgres 컨테이너를 띄우지 못했습니다. " +
                    "시도한 DOCKER_HOST=${System.getenv("DOCKER_HOST") ?: "(미설정 — 기본 소켓 /var/run/docker.sock 시도)"}. " +
                    "Docker(colima 등 비표준 소켓이면 DOCKER_HOST)를 확인하세요.",
                e,
            )
        }.also {
            Flyway.configure()
                .dataSource(it.jdbcUrl, it.username, it.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            postgres.stop()
        }
    }

    // 2026-10-29 10:00 KST
    private val today = "2026-10-29T01:00:00Z"
    private val todayDate = LocalDate.of(2026, 10, 29)

    private val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    private val jdbc = JdbcTemplate(dataSource)

    private fun adapterAt(isoInstant: String = today) =
        RateLimitCounterAdapter(dataSource, Clock.fixed(Instant.parse(isoInstant), ZoneOffset.UTC))

    @BeforeEach
    fun clean() {
        jdbc.update("DELETE FROM rate_limit_counter")
    }

    private fun usedOf(scope: String, key: String): Int? =
        jdbc.queryForList(
            "SELECT used FROM rate_limit_counter WHERE scope = ? AND limit_key = ?",
            Int::class.java, scope, RateLimitCounterAdapter.hash(key),
        ).firstOrNull()

    @Test
    fun `엔티티 선언이 V14 실제 스키마와 일치한다 - prod ddl-auto validate 가 통과한다`() {
        val registry = StandardServiceRegistryBuilder()
            .applySetting("hibernate.connection.url", postgres.jdbcUrl)
            .applySetting("hibernate.connection.username", postgres.username)
            .applySetting("hibernate.connection.password", postgres.password)
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("hibernate.hbm2ddl.auto", "validate") // prod 와 같은 설정 — SessionFactory 생성 시점에 검증한다
            .build()
        try {
            val metadata = MetadataSources(registry).addAnnotatedClass(RateLimitCounterEntity::class.java).buildMetadata()
            metadata.buildSessionFactory().close() // 불일치면 SchemaManagementException
        } finally {
            StandardServiceRegistryBuilder.destroy(registry)
        }
    }

    @Test
    fun `용량 경계 - cap 건만 통과하고 그 뒤는 거부되며 거부는 사용량을 늘리지 않는다`() {
        val adapter = adapterAt()

        val results = (1..5).map { adapter.tryConsume("s", "k", 3) != null }

        assertThat(results).containsExactly(true, true, true, false, false)
        assertThat(usedOf("s", "k")).isEqualTo(3)
    }

    @Test
    fun `날짜 분리 - KST 날짜가 바뀌면 같은 키가 새로 센다`() {
        assertThat(adapterAt("2026-10-29T14:59:00Z").tryConsume("s", "k", 1)).isNotNull() // 23:59 KST
        assertThat(adapterAt("2026-10-29T14:59:30Z").tryConsume("s", "k", 1)).isNull()
        assertThat(adapterAt("2026-10-29T15:00:00Z").tryConsume("s", "k", 1)).isNotNull() // 00:00 KST 다음 날
    }

    @Test
    fun `scope 분리 - 같은 키도 scope 가 다르면 따로 센다`() {
        val adapter = adapterAt()
        assertThat(adapter.tryConsume("daily-explain", "ip", 1)).isNotNull()

        assertThat(adapter.tryConsume("daily-explain", "ip", 1)).isNull()
        assertThat(adapter.tryConsume("daily-evaluate", "ip", 1)).isNotNull()
    }

    @Test
    fun `환불 - 사용량이 줄고 0 아래로 내려가지 않는다`() {
        val adapter = adapterAt()
        assertThat(adapter.tryConsume("s", "k", 1)).isNotNull()
        assertThat(adapter.tryConsume("s", "k", 1)).isNull()

        adapter.refund("s", "k", todayDate)
        adapter.refund("s", "k", todayDate)
        adapter.refund("s", "no-row", todayDate)

        assertThat(usedOf("s", "k")).isEqualTo(0)
        assertThat(usedOf("s", "no-row")).isNull()
        assertThat(adapter.tryConsume("s", "k", 1)).isNotNull()
    }

    @Test
    fun `updated_at 은 앱이 넣는 KST 시각이 아니라 DB NOW 라서 DEFAULT 로 들어간 행과 같은 시계다 (QA F-4)`() {
        // 앱 시계는 2026-10-29 10:00 KST 로 고정 — 앱이 시각을 바인딩하면 실제 DB 시각과 크게 어긋난다.
        val adapter = adapterAt()
        jdbc.update(
            "INSERT INTO rate_limit_counter (scope, limit_key, limit_date, used) VALUES ('default-row', 'x', CAST('2026-10-29' AS DATE), 1)",
        )
        fun updatedAt(scope: String): LocalDateTime =
            requireNotNull(jdbc.queryForObject("SELECT updated_at FROM rate_limit_counter WHERE scope = ?", LocalDateTime::class.java, scope))
        fun reset() = jdbc.update("UPDATE rate_limit_counter SET updated_at = TIMESTAMP '2000-01-01 00:00:00' WHERE scope = 's'")
        val defaultStamp = updatedAt("default-row")

        adapter.tryConsume("s", "k", 3) // INSERT 경로
        assertThat(Duration.between(defaultStamp, updatedAt("s")).abs()).describedAs("INSERT").isLessThan(Duration.ofMinutes(1))
        reset()
        adapter.tryConsume("s", "k", 3) // UPDATE 경로
        assertThat(Duration.between(defaultStamp, updatedAt("s")).abs()).describedAs("증가").isLessThan(Duration.ofMinutes(1))
        reset()
        adapter.refund("s", "k", todayDate)
        assertThat(Duration.between(defaultStamp, updatedAt("s")).abs()).describedAs("환불").isLessThan(Duration.ofMinutes(1))
    }

    @Test
    fun `2일 정리 - 보관 기간이 지난 행만 지우고 두 번째 호출은 아무것도 지우지 않는다`() {
        val adapter = adapterAt()
        listOf("2026-10-25", "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29").forEachIndexed { i, date ->
            jdbc.update(
                "INSERT INTO rate_limit_counter (scope, limit_key, limit_date, used, updated_at) VALUES ('s', ?, CAST(? AS DATE), 1, NOW())",
                RateLimitCounterAdapter.hash("k$i"), date,
            )
        }

        assertThat(adapter.deleteExpired()).isEqualTo(2)
        assertThat(adapter.deleteExpired()).isEqualTo(0)
        assertThat(jdbc.queryForList("SELECT to_char(limit_date, 'YYYY-MM-DD') FROM rate_limit_counter ORDER BY limit_date", String::class.java))
            .containsExactly("2026-10-27", "2026-10-28", "2026-10-29")
    }

    @Test
    fun `동시성 - 같은 키에 64 요청이 동시에 와도 정확히 cap 건만 성공한다`() {
        val cap = 10
        val threads = 16
        val adapter = adapterAt()
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val success = AtomicInteger()
        try {
            val futures = (1..64).map {
                pool.submit {
                    start.await()
                    if (adapter.tryConsume("s", "hot", cap) != null) success.incrementAndGet()
                }
            }
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertThat(success.get()).isEqualTo(cap)
        assertThat(usedOf("s", "hot")).isEqualTo(cap)
    }

    @Test
    fun `동시성 - 행이 아직 없는 첫 요청 경합에서도 cap 을 넘지 않는다 (INSERT 경합 복구)`() {
        val keys = 40
        val perKey = 8
        val adapter = adapterAt()
        val pool = Executors.newFixedThreadPool(perKey)
        val successByKey = Array(keys) { AtomicInteger() }
        try {
            val futures = (0 until keys).flatMap { k ->
                // 키마다 perKey 개 스레드가 장벽에서 동시에 출발 — 같은 순간 "행 없음"을 보고 INSERT 를 시도한다
                val barrier = CyclicBarrier(perKey)
                (1..perKey).map {
                    pool.submit {
                        barrier.await(30, TimeUnit.SECONDS)
                        if (adapter.tryConsume("race", "key-$k", 1) != null) successByKey[k].incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertThat(successByKey.map { it.get() }).describedAs("키마다 정확히 1건").allMatch { it == 1 }
    }

    @Test
    fun `동시성 - 첫 사용 경합에서 cap 이 2 이상이어도 키마다 정확히 cap 건이 성공한다 (QA F-1 거짓 429 회귀)`() {
        val keys = 100
        val perKey = 8
        val cap = 5
        val adapter = adapterAt()
        val pool = Executors.newFixedThreadPool(perKey)
        val successByKey = Array(keys) { AtomicInteger() }
        try {
            val futures = (0 until keys).flatMap { k ->
                val barrier = CyclicBarrier(perKey)
                (1..perKey).map {
                    pool.submit {
                        barrier.await(30, TimeUnit.SECONDS)
                        if (adapter.tryConsume("race-cap", "key-$k", cap) != null) successByKey[k].incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        // 8 요청 중 cap(5) 건만 성공해야 한다 — 첫 INSERT 경합에서 거짓 거부가 나면 5 미만이 된다
        assertThat(successByKey.map { it.get() }).describedAs("키마다 정확히 cap 건").allMatch { it == cap }
        assertThat((0 until keys).map { usedOf("race-cap", "key-$it") }).allMatch { it == cap }
    }

    @Test
    fun `쿼리 타임아웃 - 행 잠금에 막힌 쿼리는 약 1초 뒤 DataAccessException 으로 끝난다`() {
        val adapter = adapterAt()
        assertThat(adapter.tryConsume("s", "locked", 5)).isNotNull()

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { locker ->
            locker.autoCommit = false
            locker.createStatement().use { it.execute("SELECT * FROM rate_limit_counter WHERE scope = 's' FOR UPDATE") }

            val startedAt = System.nanoTime()
            assertThatThrownBy { adapter.tryConsume("s", "locked", 5) }.isInstanceOf(DataAccessException::class.java)
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            assertThat(elapsedMillis).describedAs("쿼리 타임아웃 1초").isBetween(800L, 4_000L)
            locker.rollback()
        }
    }

    @Test
    fun `긴 키와 한글 키도 64자 해시로 저장된다`() {
        val adapter = adapterAt()

        assertThat(adapter.tryConsume("s", "가".repeat(3_000), 1)).isNotNull()
        assertThat(adapter.tryConsume("s", "가".repeat(3_000), 1)).isNull()
        assertThat(jdbc.queryForList("SELECT length(limit_key) FROM rate_limit_counter", Int::class.java)).containsExactly(64)
    }
}
