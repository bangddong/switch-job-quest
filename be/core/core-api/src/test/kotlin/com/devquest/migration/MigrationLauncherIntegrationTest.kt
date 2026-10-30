package com.devquest.migration

import java.sql.DriverManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * 마이그레이션 모드(`migrate`/`validate`/`repair`)를 진짜 Postgres 로 검증한다.
 * DB 접속은 prod 와 같은 설정 소스(`storage.datasource.core.*`)를 커맨드라인 인자로 덮어써 주입한다.
 * Docker/colima 설정은 [com.devquest.migration.FlywayMigrationIntegrationTest] 주석 참고.
 */
class MigrationLauncherIntegrationTest {

    companion object {
        private lateinit var postgres: PostgreSQLContainer

        @JvmStatic
        @BeforeAll
        fun start() {
            postgres = PostgreSQLContainer("postgres:17.10").apply { start() }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            if (::postgres.isInitialized) postgres.stop()
        }
    }

    private fun dbArgs(): Array<String> = arrayOf(
        "--storage.datasource.core.driver-class-name=org.postgresql.Driver",
        "--storage.datasource.core.jdbc-url=${postgres.jdbcUrl}",
        "--storage.datasource.core.username=${postgres.username}",
        "--storage.datasource.core.password=${postgres.password}",
    )

    private fun args(mode: String): Array<String> = arrayOf(mode) + dbArgs()

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { c ->
            c.createStatement().use { s -> s.executeQuery(sql).use { read(it) } }
        }

    private fun exec(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { c ->
            c.createStatement().use { it.execute(sql) }
        }
    }

    private fun checksumOfV2(): Int = query("SELECT checksum FROM flyway_schema_history WHERE version = '2'") {
        it.next(); it.getInt(1)
    }

    private fun historyRows(): Int = query("SELECT count(*) FROM flyway_schema_history WHERE success") {
        it.next(); it.getInt(1)
    }

    @BeforeEach
    fun resetSchema() {
        exec("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
    }

    @Test
    fun `빈 DB 에 migrate 모드는 전 버전을 적용하고 exit 0`() {
        assertThat(MigrationLauncher.run(args("migrate"))).isEqualTo(0)

        assertThat(historyRows()).isEqualTo(14)
    }

    @Test
    fun `체크섬이 어긋난 이력에서 migrate 모드는 non-zero 로 실패하고 이력을 고치지 않는다`() {
        assertThat(MigrationLauncher.run(args("migrate"))).isEqualTo(0)
        exec("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '2'")
        val tampered = checksumOfV2()

        val code = MigrationLauncher.run(args("migrate"))

        assertThat(code).isNotEqualTo(0)
        // 조용한 repair 가 없다 — 어긋난 값이 그대로 남아 있어야 한다.
        assertThat(checksumOfV2()).isEqualTo(tampered)
    }

    @Test
    fun `같은 상태에서 repair 모드는 체크섬을 정렬하고 이후 migrate 가 성공한다`() {
        assertThat(MigrationLauncher.run(args("migrate"))).isEqualTo(0)
        val original = checksumOfV2()
        exec("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '2'")
        assertThat(MigrationLauncher.run(args("validate"))).isNotEqualTo(0)

        assertThat(MigrationLauncher.run(args("repair"))).isEqualTo(0)

        assertThat(checksumOfV2()).isEqualTo(original)
        assertThat(MigrationLauncher.run(args("validate"))).isEqualTo(0)
        assertThat(MigrationLauncher.run(args("migrate"))).isEqualTo(0)
    }

    @Test
    fun `기동 경로 FlywayConfig 도 repair 없이 체크섬 불일치에서 실패한다`() {
        val dataSource = org.springframework.jdbc.datasource.DriverManagerDataSource(
            postgres.jdbcUrl, postgres.username, postgres.password,
        )
        com.devquest.storage.db.core.config.FlywayConfig().flyway(dataSource)
        exec("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '2'")
        val tampered = checksumOfV2()

        org.assertj.core.api.Assertions.assertThatThrownBy {
            com.devquest.storage.db.core.config.FlywayConfig().flyway(dataSource)
        }.isInstanceOf(org.flywaydb.core.api.exception.FlywayValidateException::class.java)

        assertThat(checksumOfV2()).isEqualTo(tampered)
    }

    @Test
    fun `validate 모드는 빈 DB 에 아무것도 적용하지 않는다`() {
        MigrationLauncher.run(args("validate"))

        val tables = query("SELECT count(*) FROM information_schema.tables WHERE table_name = 'users'") {
            it.next(); it.getInt(1)
        }
        assertThat(tables).isEqualTo(0)
    }

    @Test
    fun `마이그레이션 컨텍스트는 웹 서버와 스케줄러 없이 뜨고 닫힌다`() {
        val app = MigrationLauncher.application(dbArgs())
        val context = app.run(*dbArgs())

        try {
            assertThat(context).isNotInstanceOf(WebServerApplicationContext::class.java)
            assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor::class.java)).isEmpty()
            assertThat(context.containsBean("dailyMailScheduler")).isFalse()
            assertThat(context.containsBean("rateLimitResetScheduler")).isFalse()
        } finally {
            context.close()
        }
        assertThat(context.isActive).isFalse()

        // run() 은 컨텍스트를 스스로 닫고 돌아온다(프로세스가 끝나지 않고 매달리지 않는다).
        assertThat(MigrationLauncher.run(args("migrate"))).isEqualTo(0)
    }
}
