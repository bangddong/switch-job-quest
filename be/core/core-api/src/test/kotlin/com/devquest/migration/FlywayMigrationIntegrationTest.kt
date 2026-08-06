package com.devquest.migration

import java.sql.DriverManager
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * "마이그레이션 SQL이 CI에서 한 번도 실행되지 않는다" 구멍을 막는 테스트.
 *
 * core-api의 db-core.yml(H2 in-memory + ddl-auto: create-drop)은 JPA가 엔티티로 테이블을 만들 뿐
 * Flyway SQL을 전혀 태우지 않는다. 이 테스트는 [FlywayConfig]가 prod에서 하는 것과 동일한 방식
 * (`Flyway.configure().dataSource(...).locations("classpath:db/migration").load()`)으로
 * **진짜 Postgres**에 전체 마이그레이션을 실행한다.
 *
 * ⚠️ 의도적으로 Spring 컨텍스트를 띄우지 않는다 — 순수 JUnit + Testcontainers.
 * `scanBasePackages=["com.devquest"]` 빈 누수로 43개 테스트가 깨진 전력이 있어(Phase 0),
 * 이 테스트는 Flyway + DataSource만 필요하므로 컨텍스트 로딩 자체가 불필요한 위험이다.
 *
 * 마이그레이션 리소스는 core-api(V1~V6, V8, V9)와 db-core(V7, V10~V13) 두 모듈에 나뉘어 있고
 * core-api가 db-core를 의존하므로, 이 테스트 소스셋(core-api `test`)의 클래스패스에서
 * 실제 배포와 동일하게 둘 다 합쳐진 상태로 로드된다.
 *
 * 로컬에서 colima로 Docker를 쓰는 경우(Docker Desktop이 아님) `DOCKER_HOST`가 기본 소켓
 * (`/var/run/docker.sock`)을 가리키지 않아 컨테이너 탐지에 실패할 수 있고, colima의 소켓 마운트
 * 특성상 Ryuk(리소스 리퍼) 기동이 막힐 수 있다(`operation not supported`). 이 경우:
 * `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true`
 * 를 실행 환경에 설정하면 된다. GitHub Actions `ubuntu-latest`는 기본 소켓을 그대로 쓰므로
 * CI에서는 이 설정이 필요 없다. Ryuk을 꺼도 컨테이너 정리는 이 클래스의 `@AfterAll`이 수동으로 한다.
 */
class FlywayMigrationIntegrationTest {

    companion object {
        // RDS(EKS 학습 환경, infra/aws-eks/2-cluster/variables.tf의 db_engine_version)와
        // 메이저·마이너 버전을 맞춘다 — 근거 없이 다른 버전을 고르지 않는다.
        private const val POSTGRES_IMAGE = "postgres:17.10"

        private lateinit var postgres: PostgreSQLContainer

        @JvmStatic
        @BeforeAll
        fun startContainer() {
            postgres = try {
                PostgreSQLContainer(POSTGRES_IMAGE).apply { start() }
            } catch (e: Exception) {
                // 🔴 도커가 없다고 조용히 스킵하면 안 된다 — "통과했다고 믿게 만드는 검사"가 되어
                // 이 테스트의 존재 이유(마이그레이션을 실제로 검증)가 사라진다. 명확한 실패로 죽인다.
                throw IllegalStateException(
                    "Testcontainers가 Postgres 컨테이너를 띄우지 못했습니다. 로컬(또는 CI 러너)의 Docker가 " +
                        "실행 중인지 확인하세요. 이 테스트는 Docker 부재 시 스킵되지 않고 반드시 실패합니다.",
                    e,
                )
            }
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            if (::postgres.isInitialized) {
                postgres.stop()
            }
        }

        // 마이그레이션 파일이 사라지거나(예: 리소스 누락) 추가되면 이 목록과 어긋나 테스트가 실패한다.
        private val expectedVersions = (1..13).map { it.toString() }
    }

    private fun newFlyway(): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()

    /**
     * 각 테스트를 완전히 빈 스키마에서 시작시킨다 — 테스트 간 순서 의존성을 없애고,
     * "빈 Postgres에 깨끗이 적용된다"는 주장을 매 테스트에서 실제로 검증한다.
     */
    @BeforeEach
    fun resetSchema() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
            }
        }
    }

    @Test
    fun `빈 Postgres에 전체 마이그레이션이 깨끗이 적용되고 validate를 통과한다`() {
        val flyway = newFlyway()

        val result = flyway.migrate()

        assertThat(result.success).isTrue()

        val appliedVersions = flyway.info().all().map { it.version.toString() }
        // 적용된 마이그레이션 개수/최종 버전을 단언한다 — 파일이 클래스패스에서 사라지면 여기서 잡힌다.
        assertThat(appliedVersions)
            .describedAs("적용된 마이그레이션 버전 목록")
            .containsExactlyInAnyOrderElementsOf(expectedVersions)
        assertThat(flyway.info().current().version.toString()).isEqualTo("13")

        // validate()는 실패 시 예외를 던진다 — 던지지 않으면 통과.
        flyway.validate()
    }

    @Test
    fun `마이그레이션 버전 번호가 모듈 경계를 넘어 중복되지 않는다`() {
        // V8이 core-api·db-core 양쪽에 중복 생성돼 prod 부팅이 실패했던 사고(2026-07-01, PR #231→#233)
        // 재발 방지 가드. Flyway 자체도 중복 버전을 발견하면 migrate() 단계에서 예외를 던지지만,
        // 그 의도를 이름 있는 테스트로 명시적으로 고정한다.
        val flyway = newFlyway()
        flyway.migrate()

        val versions = flyway.info().all().map { it.version.toString() }

        assertThat(versions)
            .describedAs("적용된 마이그레이션 버전 목록에 중복이 없어야 함")
            .doesNotHaveDuplicates()
    }

    @Test
    fun `동일 마이그레이션을 재실행해도 아무 것도 다시 적용되지 않는다`() {
        val flyway = newFlyway()
        flyway.migrate()

        val second = flyway.migrate()

        assertThat(second.migrationsExecuted).isEqualTo(0)
        flyway.validate()
    }
}
