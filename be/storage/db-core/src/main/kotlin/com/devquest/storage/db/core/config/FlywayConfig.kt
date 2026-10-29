package com.devquest.storage.db.core.config

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

/**
 * Flyway 마이그레이션 실행 게이트.
 *
 * ⚠️ 왜 `@Profile("prod")`만으로는 부족한가:
 * 이 클래스는 `db-core` 모듈에 있다. `db-core`를 의존하는 앱은 그 사실만으로 이 `@Configuration`이
 * 컴포넌트 스캔 대상이 되고, prod 프로파일로 뜨는 순간 자동으로 [flyway]가 생성되어
 * `migrate()`가 돈다 — "db-core를 의존한다"가 실행 조건이 되어버린다.
 *
 * 문제는 마이그레이션 파일이 `core-api`(V1~V6, V8, V9)와 `db-core`(V7, V10~V14) 두 모듈에
 * 나뉘어 있다는 점이다. `db-core`만 의존하는 앱(예: daily-api)의 클래스패스에는 `core-api` 쪽 파일이
 * 없다. 그런 앱이 `repair()`를 돌리면 "실행 시점 클래스패스의 파일 집합"과 DB의 `flyway_schema_history`를
 * 대조해 core-api 버전들이 `DELETED`로 마킹되고, 다음 core-api 기동이 그 버전을 V1부터 재실행 →
 * `CREATE TABLE` 충돌로 영구 부팅 불가에 빠진다(2026-07-01 V8 사고, PR #231→#233과 동일한 실패 모드).
 *
 * 그래서 "db-core를 의존한다"가 아니라 `devquest.flyway.migrate-on-startup=true`를 명시적으로
 * 켠 앱만 마이그레이션을 실행하게 한다. 기본값은 반드시 false(off) — 새 앱이 아무 설정도 안 해도
 * 안전해야 한다("잊으면 터진다"가 아니라 "잊으면 안 돈다"가 맞는 방향). core-api는
 * `application-prod.yml`에서 이 값을 true로 켜서 기존 동작(prod 기동 시 자동 마이그레이션)을
 * 그대로 유지한다.
 *
 * `repair()`는 이 기동 경로에서 **제거**됐다(D-016 순서 1a). `repair()`는 체크섬 불일치를 조용히 덮고, 클래스패스가
 * 다른 이미지(중간 버전이 빠진 이미지, PR 이미지의 다른 V14, 재번호 핫픽스)가 돌리면 적용된 버전을
 * DELETED 로 마킹하기 때문이다. 이제 기동 경로는 `migrate()`만 호출한다 — Flyway 기본 `validateOnMigrate`가
 * 먼저 검증하므로 체크섬 불일치는 **조용히 고쳐지지 않고 기동 실패**로 드러난다. repair 는 사람이 명시적으로
 * 치는 `java -jar app.jar repair`(com.devquest.migration, core-api)로만 가능하다.
 *
 * 환경별 실행 주체: Fly 는 배포 단계(`release_command = "migrate"`)에서 마이그레이션하고 앱 기동에서는
 * `DEVQUEST_FLYWAY_MIGRATE_ON_STARTUP=false`로 끈다. EKS 는 당분간 `application-prod.yml`의 기본값(true)대로
 * 기동 시 `migrate()`를 쓴다(EKS Job 으로의 분리는 후속 작업).
 */
@Configuration
@Profile("prod")
@ConditionalOnProperty(
    prefix = "devquest.flyway",
    name = ["migrate-on-startup"],
    havingValue = "true",
    matchIfMissing = false,
)
class FlywayConfig {

    @Bean
    fun flyway(@Qualifier("coreDataSource") dataSource: DataSource): Flyway {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
        flyway.migrate()
        return flyway
    }

    @Bean
    fun flywayEntityManagerFactoryDependsOn(): EntityManagerFactoryDependsOnPostProcessor {
        return EntityManagerFactoryDependsOnPostProcessor("flyway")
    }
}
