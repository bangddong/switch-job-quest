package com.devquest.migration

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * [MigrationMode] 별 Flyway 동작과 종료 코드. 예외를 삼키지 않는다 — 로그로 남기고 non-zero 를 돌려준다.
 */
object MigrationExecutor {
    private val log = LoggerFactory.getLogger(javaClass)

    const val EXIT_OK = 0
    const val EXIT_FAILED = 1

    /**
     * core-api jar 가 전체 마이그레이션 집합(core-api V1~V6·V8·V9 + db-core V7·V10~V14)을 가진 유일한 이미지다.
     * validate 모드만 pending 을 허용한다(이력 일관성만 보려는 사전 점검이라서). migrate 모드는 Flyway 기본
     * validateOnMigrate=true 로 migrate 직전에 같은 검증을 하며, 거기서는 pending 이 정상이다.
     */
    fun newFlyway(dataSource: DataSource, mode: MigrationMode): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .apply { if (mode == MigrationMode.VALIDATE) ignoreMigrationPatterns("*:pending") }
            .load()

    fun execute(mode: MigrationMode, flyway: Flyway): Int =
        try {
            when (mode) {
                MigrationMode.MIGRATE -> {
                    val result = flyway.migrate()
                    log.info(
                        "[migration] migrate 성공: 이번에 적용 {}건, 현재 버전 {}",
                        result.migrationsExecuted,
                        flyway.info().current()?.version ?: "(없음)",
                    )
                }
                MigrationMode.VALIDATE -> {
                    flyway.validate()
                    log.info("[migration] validate 성공: 현재 버전 {}", flyway.info().current()?.version ?: "(없음)")
                }
                MigrationMode.REPAIR -> {
                    log.warn("=".repeat(78))
                    log.warn("[migration] *** REPAIR 모드 — flyway_schema_history 를 수정합니다 (수동 전용) ***")
                    log.warn("[migration] 체크섬 불일치를 덮고 클래스패스에 없는 버전을 DELETED 로 마킹할 수 있습니다.")
                    log.warn("=".repeat(78))
                    val result = flyway.repair()
                    log.warn(
                        "[migration] repair 완료: 체크섬 정렬 {}건, 삭제 마킹 {}건, 제거 {}건",
                        result.migrationsAligned.size,
                        result.migrationsDeleted.size,
                        result.migrationsRemoved.size,
                    )
                }
            }
            EXIT_OK
        } catch (e: Exception) {
            log.error("[migration] {} 실패 — repair 는 자동으로 하지 않습니다(필요하면 수동 repair 모드): {}", mode.arg, e.message, e)
            EXIT_FAILED
        }
}
