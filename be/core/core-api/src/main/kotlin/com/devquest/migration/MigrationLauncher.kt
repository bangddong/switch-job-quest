package com.devquest.migration

import com.devquest.storage.db.core.config.CoreDataSourceConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import javax.sql.DataSource

/**
 * 마이그레이션 모드 진입점 — `main()` 이 첫 인자가 `migrate`/`validate`/`repair` 일 때 호출한다.
 *
 * 전체 앱 컨텍스트(`@SpringBootApplication` 스캔)를 띄우지 않는다. 스캔하면 웹 서버,
 * `@EnableScheduling` 스케줄러(프로세스가 안 끝난다), 메일·AI·OTLP, GITHUB/JWT 시크릿 요구가 딸려온다.
 * 대신 [CoreDataSourceConfig] 하나만 가져온 최소 컨텍스트(`WebApplicationType.NONE`)를 쓴다 —
 * prod 앱과 같은 설정 소스(`storage.datasource.core.*` ← application-prod.yml ← DB_HOST 등 env)로 DataSource 가 구성된다.
 *
 * 이 클래스는 일부러 `@Configuration`/`@Component` 가 아니다 — 있으면 DevQuestApplication 의 스캔에 잡힌다.
 */
object MigrationLauncher {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 최소 컨텍스트 구성. 어노테이션 처리는 `@Configuration` 이 아니어도 등록된 소스 클래스에 적용된다. */
    @Import(CoreDataSourceConfig::class)
    @EnableConfigurationProperties
    internal class MigrationContextConfig

    internal fun application(@Suppress("UNUSED_PARAMETER") args: Array<String>): SpringApplicationBuilder =
        SpringApplicationBuilder(MigrationContextConfig::class.java)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)

    /** @param args 첫 원소는 모드, 나머지는 Spring 커맨드라인 인자(`--key=value`). @return 종료 코드 */
    fun run(args: Array<String>): Int {
        val mode = MigrationMode.parse(args) ?: return MigrationExecutor.EXIT_FAILED
        val springArgs = args.drop(1).toTypedArray()
        return try {
            application(springArgs).run(*springArgs).use { context ->
                val dataSource = context.getBean(DataSource::class.java)
                MigrationExecutor.execute(mode, MigrationExecutor.newFlyway(dataSource, mode))
            }
        } catch (e: Exception) {
            // 컨텍스트 기동 실패(DB 접속 불가 등) — 삼키지 않고 non-zero.
            log.error("[migration] {} 실행 불가(컨텍스트/DB 접속 실패): {}", mode.arg, e.message, e)
            MigrationExecutor.EXIT_FAILED
        }
    }
}
