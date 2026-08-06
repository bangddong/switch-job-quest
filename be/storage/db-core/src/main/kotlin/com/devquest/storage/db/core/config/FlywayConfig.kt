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
 * `repair()` + `migrate()`가 돈다 — "db-core를 의존한다"가 실행 조건이 되어버린다.
 *
 * 문제는 마이그레이션 파일이 `core-api`(V1~V6, V8, V9)와 `db-core`(V7, V10~V13) 두 모듈에
 * 나뉘어 있다는 점이다. `repair()`는 "실행 시점 클래스패스의 마이그레이션 파일 집합"과 DB의
 * `flyway_schema_history`를 대조해, 없는 버전을 `DELETED`로 마킹한다(flyway-core 문서:
 * "Repair must be given the same locations as migrate"). `db-core`만 의존하는 앱(예: 계획 중인
 * daily-api)의 클래스패스에는 `core-api` 쪽 파일이 없으므로, 그 앱이 한 번이라도 `repair()`를
 * 돌리면 core-api 버전들이 공유 이력에서 `DELETED`로 마킹된다. 그 다음 core-api가 기동하면
 * 그 버전들을 "미적용"으로 보고 V1부터 재실행 → `CREATE TABLE`이 기존 테이블에 부딪혀
 * 영구 부팅 불가에 빠진다(2026-07-01 V8 사고, PR #231→#233과 동일한 실패 모드).
 * 즉 daily-api의 단순 기동 1회가 core-api를 영구 사망시킬 수 있다.
 *
 * 그래서 "db-core를 의존한다"가 아니라 `devquest.flyway.migrate-on-startup=true`를 명시적으로
 * 켠 앱만 마이그레이션을 실행하게 한다. 기본값은 반드시 false(off) — 새 앱이 아무 설정도 안 해도
 * 안전해야 한다("잊으면 터진다"가 아니라 "잊으면 안 돈다"가 맞는 방향). core-api는
 * `application-prod.yml`에서 이 값을 true로 켜서 기존 동작(prod 기동 시 자동 마이그레이션)을
 * 그대로 유지한다.
 *
 * `repair()` 호출 자체는 이번 게이트 작업의 범위가 아니다 — "누가 실행하는가"만 다루고
 * "무엇을 하는가"는 그대로 둔다. core-api 단독으로 도는 지금은 안전하다.
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
        flyway.repair()
        flyway.migrate()
        return flyway
    }

    @Bean
    fun flywayEntityManagerFactoryDependsOn(): EntityManagerFactoryDependsOnPostProcessor {
        return EntityManagerFactoryDependsOnPostProcessor("flyway")
    }
}
