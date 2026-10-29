package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.port.RateLimitCounterPort
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.sql.DataSource

/**
 * [RateLimitCounterPort] 의 JDBC 구현 — core-api·daily-api 가 이 한 벌을 공유한다.
 *
 * 🔴 JPA(EntityManager)가 아니라 **JdbcTemplate** 이다. 호출처는 인터셉터(`preHandle`)라 OSIV 가 켜진 요청 스레드이고,
 * 거기서 JPA 로 쿼리하면 EntityManager 가 커넥션을 요청 끝까지 쥔다(DELAYED_ACQUISITION_AND_HOLD). 이 요청은 그 뒤 AI 호출로
 * 최대 150초를 쓰므로 풀이 마른다. JdbcTemplate 은 트랜잭션 밖에서 문장마다 커넥션을 빌렸다 즉시 돌려준다.
 *
 * 쿼리 타임아웃은 1초다 — 한도 검사 때문에 요청이 오래 막히지 않게 한다(호출처는 실패 시 메모리 버킷으로 대체한다).
 *
 * ## 원자 차감 (Postgres·H2 공통 문법)
 * ```
 * 1) UPDATE … SET used = used + 1 WHERE (scope,key,date) AND used < :cap     → 1행이면 차감 성공
 * 2) 0행이면 INSERT … SELECT … WHERE NOT EXISTS (같은 PK)                      → 1행이면 첫 사용(used=1) 성공
 * 3) INSERT 도 0행이면 행이 있고 이미 상한 → 거부. (동시 INSERT 경합으로 PK 충돌이 나면 1)을 한 번 더)
 * ```
 * 한 문장짜리 `INSERT … ON CONFLICT DO UPDATE … WHERE … RETURNING` 대신 이 형태를 쓰는 이유: H2 가 `ON CONFLICT DO UPDATE` 를
 * 지원하지 않아 테스트 컨텍스트(H2)가 실제 SQL 을 타지 못한다. 상한은 `UPDATE … WHERE used < :cap` 의 행 잠금이 지키므로
 * 동시 N 요청이어도 정확히 cap 건만 성공한다(core-api Testcontainers 테스트가 실제 Postgres 로 검증).
 * 상한 도달 후의 거부 요청이 INSERT 를 시도해 PK 위반 에러 로그를 쌓지 않도록 `NOT EXISTS` 로 거른다.
 *
 * 날짜는 앱이 KST 로 계산해 바인딩한다 — DB `CURRENT_DATE` 는 EC2 Postgres 의 TZ(미설정)에 의존한다.
 */
@Component
class RateLimitCounterAdapter(
    dataSource: DataSource,
    private val clock: Clock,
) : RateLimitCounterPort {

    @Autowired
    constructor(dataSource: DataSource) : this(dataSource, Clock.system(KST))

    private val jdbc = JdbcTemplate(dataSource).apply { queryTimeout = QUERY_TIMEOUT_SECONDS }

    override fun tryConsume(scope: String, key: String, capacity: Int): Boolean {
        if (capacity <= 0) return false
        val hashedKey = hash(key)
        val today = today()
        val now = now()

        if (increment(scope, hashedKey, today, capacity, now)) return true
        val inserted = try {
            jdbc.update(INSERT_FIRST_USE, scope, hashedKey, today, now, scope, hashedKey, today)
        } catch (e: DuplicateKeyException) {
            // 다른 인스턴스가 같은 순간 첫 행을 만들었다 — 이제 행이 있으니 증가 경로로 다시 간다.
            return increment(scope, hashedKey, today, capacity, now)
        }
        return inserted == 1
    }

    override fun refund(scope: String, key: String) {
        jdbc.update(REFUND, now(), scope, hash(key), today())
    }

    override fun deleteExpired(): Int =
        jdbc.update(DELETE_EXPIRED, today().minusDays(RETENTION_DAYS))

    private fun increment(scope: String, hashedKey: String, today: LocalDate, capacity: Int, now: Timestamp): Boolean =
        jdbc.update(INCREMENT, now, scope, hashedKey, today, capacity) == 1

    private fun today(): LocalDate = LocalDate.now(clock.withZone(KST))

    private fun now(): Timestamp = Timestamp.valueOf(LocalDateTime.now(clock.withZone(KST)))

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private const val QUERY_TIMEOUT_SECONDS = 1

        /** 지난 날짜 행 보관 일수. `limit_date < 오늘 - 2일` 인 행을 지운다. */
        const val RETENTION_DAYS = 2L

        private const val INCREMENT =
            "UPDATE rate_limit_counter SET used = used + 1, updated_at = ? " +
                "WHERE scope = ? AND limit_key = ? AND limit_date = ? AND used < ?"

        private const val INSERT_FIRST_USE =
            "INSERT INTO rate_limit_counter (scope, limit_key, limit_date, used, updated_at) " +
                "SELECT ?, ?, ?, 1, ? WHERE NOT EXISTS " +
                "(SELECT 1 FROM rate_limit_counter WHERE scope = ? AND limit_key = ? AND limit_date = ?)"

        private const val REFUND =
            "UPDATE rate_limit_counter SET used = used - 1, updated_at = ? " +
                "WHERE scope = ? AND limit_key = ? AND limit_date = ? AND used > 0"

        private const val DELETE_EXPIRED = "DELETE FROM rate_limit_counter WHERE limit_date < ?"

        /**
         * 키(IP·사용자 ID)의 SHA-256 hex(64자). 백업에 원문이 실리지 않고 키 길이가 고정된다(XFF 로 긴 문자열을 보내도 컬럼에 맞는다).
         * ⚠️ IPv4 는 공간이 작아 대입으로 역산할 수 있다 — 완전한 익명화가 아니라 "원문 평문 저장 회피"다.
         */
        fun hash(key: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(key.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
