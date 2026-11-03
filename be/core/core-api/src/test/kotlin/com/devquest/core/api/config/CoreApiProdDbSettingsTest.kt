package com.devquest.core.api.config

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
}
