package com.devquest.core.api.config

import com.devquest.core.api.support.SharedRateLimiter
import com.zaxxer.hikari.HikariConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.bootstrap.DefaultBootstrapContext
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.DefaultResourceLoader

/**
 * B7-21 ⓐ 회귀 가드 - core-api prod 프로필의 **실효** DB 타임아웃·OSIV 설정.
 *
 * 왜 yml 을 직접 파싱하지 않는가: core-api 는 `spring.config.import` 로 `db-core.yml`(H2 기본값)을 가져오고,
 * import 한 파일의 값은 importing 파일 값을 이긴다(이 레포가 #521 에서 겪은 우선순위 함정). 그래서 `application-prod.yml` 에 값이
 * 있어도 실제 Environment 에서는 다른 파일 값이 이길 수 있다. 여기서는 Boot 가 부팅 때 쓰는 [ConfigDataEnvironmentPostProcessor] 로
 * **실제 우선순위대로** 해석한 Environment 를 읽는다.
 *
 * prod 프로필 컨텍스트 전체 기동은 Postgres 접속을 요구해서 하지 않는다. 대신 `CoreDataSourceConfig.coreHikariConfig()` 가 쓰는 것과
 * 같은 [Binder] 로 [HikariConfig] 에 바인딩해, 키 이름 오타(예: `connection-timeout` -> `connect-timeout`)가 "조용히 무시"되는 것을 잡는다.
 *
 * `${DB_HOST}` 같은 플레이스홀더는 가짜 값으로 채운다(시크릿 아님, 접속하지 않음).
 */
class CoreApiProdDbSettingsTest {

    private fun prodEnvironment(): ConfigurableEnvironment {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(
            MapPropertySource(
                "test-overrides",
                mapOf(
                    // application.yml 의 `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}` 가 local 을 끌어오지 않게 prod 로 고정한다.
                    "spring.profiles.active" to "prod",
                    "DB_HOST" to "db.invalid:5432",
                    "DB_NAME" to "devquest",
                    "DB_USERNAME" to "test-only",
                    "DB_PASSWORD" to "test-only",
                    "GRAFANA_LOKI_URL" to "",
                ),
            ),
        )
        ConfigDataEnvironmentPostProcessor.applyTo(environment, DefaultResourceLoader(), DefaultBootstrapContext())
        assertThat(environment.activeProfiles).describedAs("테스트 전제: prod 프로필만 활성").containsExactly("prod")
        return environment
    }

    private fun boundHikari(environment: ConfigurableEnvironment): HikariConfig {
        val config = HikariConfig()
        Binder.get(environment).bind("storage.datasource.core", Bindable.ofInstance(config))
        return config
    }

    @Test
    fun `prod 풀은 대기 5초 검증 2초로 바인딩된다 - HikariConfig 필드에 실제로 들어간다`() {
        val hikari = boundHikari(prodEnvironment())

        assertThat(hikari.connectionTimeout).describedAs("connection-timeout: 풀 대기 상한").isEqualTo(5_000L)
        assertThat(hikari.validationTimeout).describedAs("validation-timeout: 대여 전 검증 상한").isEqualTo(2_000L)
    }

    @Test
    fun `prod 풀 크기와 검증 쿼리는 그대로다 - db-core 의 H2 기본값이 이기지 않는다`() {
        val hikari = boundHikari(prodEnvironment())

        assertThat(hikari.maximumPoolSize).isEqualTo(10)
        assertThat(hikari.minimumIdle).isEqualTo(0)
        assertThat(hikari.connectionTestQuery).isEqualTo("SELECT 1")
        assertThat(hikari.driverClassName).isEqualTo("org.postgresql.Driver")
    }

    @Test
    fun `prod JDBC URL 은 TLS 필수에 연결 5초 소켓 30초 타임아웃을 건다`() {
        val hikari = boundHikari(prodEnvironment())

        assertThat(hikari.jdbcUrl)
            .startsWith("jdbc:postgresql://db.invalid:5432/devquest?")
            .contains("sslmode=require")
            .contains("connectTimeout=5")
            .contains("socketTimeout=30")
    }

    @Test
    fun `prod 에서도 OSIV 는 꺼져 있다`() {
        val openInView = prodEnvironment().getProperty("spring.jpa.open-in-view")

        assertThat(openInView).isEqualTo("false")
    }

    @Test
    fun `레이트 리밋 시도권 리스는 한 번의 DB 시도 최대 시간보다 길다`() {
        val hikari = boundHikari(prodEnvironment())
        val socketTimeoutSeconds = Regex("""socketTimeout=(\d+)""").find(hikari.jdbcUrl)?.groupValues?.get(1)?.toLong()
            ?: throw AssertionError("jdbc-url 에 socketTimeout 이 없다: ${hikari.jdbcUrl}")

        val worstCaseAttemptMillis = worstCaseAttemptMillis(
            connectionTimeoutMillis = hikari.connectionTimeout,
            queryTimeoutMillis = ADAPTER_QUERY_TIMEOUT_MILLIS,
            socketTimeoutMillis = socketTimeoutSeconds * 1_000,
            statements = MAX_STATEMENTS_PER_ATTEMPT,
        )

        assertThat(SharedRateLimiter.PROBE_LEASE_MILLIS)
            .describedAs("PROBE_LEASE_MILLIS 가 DB 시도 최대 시간(${worstCaseAttemptMillis}ms)보다 짧으면 시도가 진행 중인데 다른 요청이 시도권을 가져간다")
            .isGreaterThan(worstCaseAttemptMillis)
    }

    @Test
    fun `최악 시간 모델 - prod 값은 47초이고 앞선 문장마다 풀 대기를 포함한다`() {
        // 2 x (5 + 1) + (5 + 30) = 47. 앞선 두 문장이 풀 대기 5초 끝에 간신히 성공하고 쿼리 1초를 써도, 마지막 문장이 다시 풀 대기 5초 + 동결 30초를 쓸 수 있다.
        assertThat(worstCaseAttemptMillis(5_000, ADAPTER_QUERY_TIMEOUT_MILLIS, 30_000, MAX_STATEMENTS_PER_ATTEMPT)).isEqualTo(47_000L)
    }

    @Test
    fun `최악 시간 모델 - connection-timeout 을 10초로 올리면 리스 60초를 넘는다`() {
        // 2 x (10 + 1) + (10 + 30) = 62. 앞선 문장의 풀 대기를 빼먹는 옛 공식은 10 + 30 + 2 = 42 라서 이 변경을 통과시켜 버렸다(거짓 통과).
        val worst = worstCaseAttemptMillis(10_000, ADAPTER_QUERY_TIMEOUT_MILLIS, 30_000, MAX_STATEMENTS_PER_ATTEMPT)

        assertThat(worst).isEqualTo(62_000L)
        assertThat(SharedRateLimiter.PROBE_LEASE_MILLIS).isLessThan(worst)
    }

    companion object {
        /**
         * `RateLimitCounterAdapter.tryConsume` 가 한 시도에서 실행하는 최대 문장 수: 증가 UPDATE, 첫 사용 INSERT, 증가 UPDATE 재시도 = 3.
         * (`refund` 는 1문장이라 더 짧다.) 어댑터에 문장을 더하면 이 값도 올릴 것.
         */
        private const val MAX_STATEMENTS_PER_ATTEMPT = 3

        /** `RateLimitCounterAdapter` 의 JdbcTemplate 쿼리 타임아웃(`QUERY_TIMEOUT_SECONDS = 1`). */
        private const val ADAPTER_QUERY_TIMEOUT_MILLIS = 1_000L

        /**
         * 한 번의 DB 시도(= 문장 [statements] 개)가 걸릴 수 있는 최악 시간.
         * 문장마다 **따로** 커넥션을 빌리므로 문장마다 풀 대기가 붙는다 - 앞선 `statements - 1` 문장은 각각 풀 대기 + 쿼리 타임아웃까지 쓰고도 성공할 수 있고,
         * 마지막 문장이 풀 대기 + 쿼리 중 동결([socketTimeoutMillis])을 쓴다.
         */
        fun worstCaseAttemptMillis(
            connectionTimeoutMillis: Long,
            queryTimeoutMillis: Long,
            socketTimeoutMillis: Long,
            statements: Int,
        ): Long = (statements - 1) * (connectionTimeoutMillis + queryTimeoutMillis) + connectionTimeoutMillis + socketTimeoutMillis
    }
}
