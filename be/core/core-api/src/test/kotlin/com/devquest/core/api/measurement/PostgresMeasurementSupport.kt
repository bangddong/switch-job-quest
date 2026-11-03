package com.devquest.core.api.measurement

import org.flywaydb.core.Flyway
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * 측정용 실제 Postgres 컨테이너(JVM 당 1개). 구성별 컨텍스트가 같은 컨테이너를 공유하므로 클래스 단위 stop 을 쓰지 않고
 * JVM 종료 훅에서 내린다(colima 에서는 루트 build.gradle.kts 가 Ryuk 를 꺼 두어 자동 정리가 없다).
 * 이미지 버전은 DailyQuestionPostgresConcurrencyTest·FlywayMigrationIntegrationTest 와 맞춘다(근거 없이 바꾸지 않는다).
 */
object PostgresMeasurementSupport {
    private const val POSTGRES_IMAGE = "postgres:17.10"

    val postgres: PostgreSQLContainer by lazy {
        val container = PostgreSQLContainer(POSTGRES_IMAGE).apply { start() }
        Flyway.configure()
            .dataSource(container.jdbcUrl, container.username, container.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { container.stop() } })
        container
    }

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("storage.datasource.core.driver-class-name") { "org.postgresql.Driver" }
        registry.add("storage.datasource.core.jdbc-url") { postgres.jdbcUrl }
        registry.add("storage.datasource.core.username") { postgres.username }
        registry.add("storage.datasource.core.password") { postgres.password }
        registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
        registry.add("spring.jpa.properties.hibernate.dialect") { "org.hibernate.dialect.PostgreSQLDialect" }
    }
}
