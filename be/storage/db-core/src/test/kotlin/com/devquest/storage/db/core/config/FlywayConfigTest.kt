package com.devquest.storage.db.core.config

import java.sql.SQLException
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.SystemEnvironmentPropertySource

/**
 * FlywayConfig가 `devquest.flyway.migrate-on-startup` 프로퍼티로만 활성화되는지 검증한다.
 *
 * ⚠️ 전체 Spring 컨텍스트(scanBasePackages=["com.devquest"])를 띄우지 않는다 — Phase 0에서 빈 누수로
 * 테스트 43개가 깨진 전력이 있다(be/CLAUDE.md, FlywayMigrationIntegrationTest 상단 주석 참고).
 * `ApplicationContextRunner`로 [FlywayConfig] 하나만 격리해 검증한다.
 *
 * 켜진 경우(ON) "게이트를 통과해 실제로 실행을 시도했는지"까지 확인하기 위해, 진짜 DB 대신
 * `getConnection()` 호출 시 즉시 `SQLException`을 던지는 mock DataSource를 쓴다.
 * - OFF: `@ConditionalOnProperty`가 빈 정의 자체를 등록하지 않으므로 DataSource는 전혀 건드리지 않는다.
 * - ON: 빈 생성이 시도되어 `flyway.migrate()`가 실제로 `DataSource.getConnection()`을 호출한다
 *   (연결이 실패해 컨텍스트 기동 자체는 실패하지만, "게이트를 통과해서 실행까지 갔다"는
 *   사실 자체가 이 테스트가 검증하려는 것이다 — 실제 마이그레이션 성공 여부는
 *   `FlywayMigrationIntegrationTest`가 진짜 Postgres로 검증하며, 기동 경로에 repair 가 없다는 것도 거기서 고정한다).
 */
class FlywayConfigTest {

    // ApplicationContextRunner.withBean(name, type, supplier) 조합의 오버로드가 없어(Kotlin에서
    // vararg BeanDefinitionCustomizer 오버로드와 충돌), 리프레시 전에 beanFactory에 직접
    // registerSingleton으로 "coreDataSource" 빈을 심는다 — mock 인스턴스를 그대로 참조 동일성 유지한 채
    // 테스트 밖에서 verify할 수 있다.
    private fun runnerWithDataSource(dataSource: DataSource): ApplicationContextRunner =
        ApplicationContextRunner()
            .withInitializer { context ->
                context.environment.addActiveProfile("prod")
                context.beanFactory.registerSingleton("coreDataSource", dataSource)
            }
            .withUserConfiguration(FlywayConfig::class.java)

    @Test
    fun `게이트 프로퍼티가 없으면 Flyway 빈이 생성되지 않고 DataSource도 건드리지 않는다`() {
        val dataSource: DataSource = mock()

        runnerWithDataSource(dataSource)
            .run { context ->
                assertThat(context.startupFailure).isNull()
                assertThat(context).doesNotHaveBean(Flyway::class.java)
                verifyNoInteractions(dataSource)
            }
    }

    @Test
    fun `게이트 프로퍼티가 true면 Flyway 빈 생성이 시도되어 DataSource 연결까지 실제로 호출된다`() {
        val dataSource: DataSource = mock()
        whenever(dataSource.connection).thenThrow(SQLException("강제 연결 실패 - 게이트 통과 검증용"))

        runnerWithDataSource(dataSource)
            .withPropertyValues("devquest.flyway.migrate-on-startup=true")
            .run { context ->
                // 연결 자체는 강제로 실패하므로 컨텍스트 기동은 실패한다 —
                // 여기서 검증하려는 건 "게이트를 통과해 DataSource까지 실제로 도달했는가"이다.
                assertThat(context.startupFailure).isNotNull()
                verify(dataSource, atLeastOnce()).connection
            }
    }

    @Test
    fun `fly toml 의 환경변수 DEVQUEST_FLYWAY_MIGRATE_ON_STARTUP=false 가 yml 의 true 를 이겨 기동 마이그레이션이 꺼진다`() {
        // be/fly.toml [env] 가 쓰는 이름이 실제로 `devquest.flyway.migrate-on-startup` 에 바인딩되는지(하이픈↔언더스코어)
        // 고정한다. 오타·바인딩 실패면 Fly 앱이 기동 시 또 마이그레이션을 돌린다.
        val dataSource: DataSource = mock()

        runnerWithDataSource(dataSource)
            .withInitializer { context ->
                // application-prod.yml 의 기본값(true)을 흉내 — 낮은 우선순위
                context.environment.propertySources.addLast(
                    MapPropertySource("yml", mapOf("devquest.flyway.migrate-on-startup" to "true")),
                )
                // OS 환경변수 — 실제 환경과 같은 SystemEnvironmentPropertySource, 높은 우선순위
                context.environment.propertySources.addFirst(
                    SystemEnvironmentPropertySource(
                        "systemEnvironment",
                        mapOf("DEVQUEST_FLYWAY_MIGRATE_ON_STARTUP" to "false"),
                    ),
                )
            }
            .run { context ->
                assertThat(context.startupFailure).isNull()
                assertThat(context).doesNotHaveBean(Flyway::class.java)
                verifyNoInteractions(dataSource)
            }
    }
}
