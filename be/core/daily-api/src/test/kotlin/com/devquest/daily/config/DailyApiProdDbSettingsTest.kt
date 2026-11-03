package com.devquest.daily.config

import com.devquest.daily.support.SharedRateLimiter
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
 * B7-21 ⓐ 회귀 가드 - daily-api prod 프로필의 **실효** DB 타임아웃·OSIV 설정.
 * core-api 의 `CoreApiProdDbSettingsTest` 와 같은 방식(Boot 의 [ConfigDataEnvironmentPostProcessor] 로 실제 우선순위대로 해석한
 * Environment + `CoreDataSourceConfig` 와 같은 [Binder] 로 [HikariConfig] 바인딩)이다. 두 앱은 같은 `storage.datasource.core.*` 키를
 * 각자의 yml 에 따로 갖고 있어 한쪽만 고치면 조용히 갈라진다 - 그래서 앱마다 가드를 둔다.
 * `${DB_HOST}` 등은 가짜 값으로 채운다(시크릿 아님, 접속하지 않음).
 */
class DailyApiProdDbSettingsTest {

    private fun prodEnvironment(): ConfigurableEnvironment {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(
            MapPropertySource(
                "test-overrides",
                mapOf(
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
    fun `prod 풀 크기와 검증 쿼리는 그대로다`() {
        val hikari = boundHikari(prodEnvironment())

        assertThat(hikari.maximumPoolSize).isEqualTo(3)
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

        // 최악: 풀 대기(connection-timeout) + 쿼리 중 동결(socketTimeout) + 그 앞 문장 2개의 쿼리 타임아웃(어댑터 1초씩).
        val worstCaseAttemptMillis = hikari.connectionTimeout + socketTimeoutSeconds * 1_000 + 2 * 1_000

        assertThat(SharedRateLimiter.PROBE_LEASE_MILLIS)
            .describedAs("PROBE_LEASE_MILLIS 가 DB 시도 최대 시간(${worstCaseAttemptMillis}ms)보다 짧으면 시도가 진행 중인데 다른 요청이 시도권을 가져간다")
            .isGreaterThan(worstCaseAttemptMillis)
    }
}
