package com.devquest.storage.db.core

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * V14 `rate_limit_counter` 의 **스키마 선언**이다 — 이 엔티티로 읽고 쓰지 않는다.
 *
 * 카운터는 [com.devquest.storage.db.core.adapter.RateLimitCounterAdapter] 가 JdbcTemplate 으로 직접 갱신한다.
 * (인터셉터에서 JPA EntityManager 로 쿼리하면 OSIV 때문에 요청 끝까지 커넥션을 쥔다 — 그 뒤 AI 호출이 최대 150초.)
 * 그래도 엔티티를 두는 이유는 둘이다.
 *  1. H2(`create-drop`) 테스트 컨텍스트에서 Hibernate 가 테이블을 만들어 준다 — 어댑터 SQL 이 실제로 돈다.
 *  2. prod(`ddl-auto: validate`)에서 마이그레이션과 어긋나면(타입·컬럼) 기동 시점에 드러난다.
 *
 * 타입은 V14 와 정확히 맞춘다: date↔LocalDate, int↔Int, timestamp↔LocalDateTime.
 * ⚠️ 두 앱(core-api·daily-api)이 모두 `@EntityScan` 으로 이 엔티티를 validate 한다 — V14 가 적용되기 전에
 * daily-api 가 먼저 뜨면 기동에 실패한다(마이그레이션은 core-api 가 실행한다).
 */
@Entity
@Table(name = "rate_limit_counter")
class RateLimitCounterEntity(
    @EmbeddedId
    val id: RateLimitCounterId = RateLimitCounterId(),

    @Column(name = "used", nullable = false)
    var used: Int = 0,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)

@Embeddable
class RateLimitCounterId(
    @Column(name = "scope", nullable = false, length = 64)
    val scope: String = "",

    @Column(name = "limit_key", nullable = false, length = 64)
    val limitKey: String = "",

    @Column(name = "limit_date", nullable = false)
    val limitDate: LocalDate = LocalDate.EPOCH,
) : Serializable {

    override fun equals(other: Any?): Boolean =
        this === other || (other is RateLimitCounterId &&
            scope == other.scope && limitKey == other.limitKey && limitDate == other.limitDate)

    override fun hashCode(): Int = arrayOf(scope, limitKey, limitDate).contentHashCode()
}
