package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.port.RateLimitCounterPort
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
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
 * 3) INSERT 가 0행이거나 PK 충돌이면 행이 이미 있다(타 요청이 방금 만들었거나 이미 상한) → 1)을 정확히 한 번 더 → 1행이면 성공, 0행이면 거부
 * ```
 * 한 문장짜리 `INSERT … ON CONFLICT DO UPDATE … WHERE … RETURNING` 대신 이 형태를 쓰는 이유: H2 가 `ON CONFLICT DO UPDATE` 를
 * 지원하지 않아 테스트 컨텍스트(H2)가 실제 SQL 을 타지 못한다. 상한은 `UPDATE … WHERE used < :cap` 의 행 잠금이 지키므로
 * 동시 N 요청이어도 정확히 cap 건만 성공한다(core-api Testcontainers 테스트가 실제 Postgres 로 검증).
 * 상한 도달 후의 거부 요청이 INSERT 를 시도해 PK 위반 에러 로그를 쌓지 않도록 `NOT EXISTS` 로 거른다.
 *
 * 날짜는 앱이 KST 로 계산해 바인딩한다 — DB `CURRENT_DATE` 는 EC2 Postgres 의 TZ(미설정)에 의존한다.
 * 차감한 날짜는 [tryConsume] 이 돌려주고, 환불은 그 날짜 행에 한다(자정을 낀 환불이 새 날짜 행을 건드리지 않는다).
 * `updated_at` 은 INSERT·UPDATE 모두 DB `NOW()` 로 채운다 — V14 의 `DEFAULT NOW()` 와 같은 시계라서 한 컬럼에 시각 기준이 섞이지 않는다
 * (앱이 KST 벽시계를 바인딩하면 DB 세션 TZ 와 어긋난다).
 *
 * ## 알려진 한계 (고치지 않는다)
 * 쿼리 타임아웃(1초)이 **커밋 이후** 에 발생하면 DB 에는 차감이 남았는데 호출처는 실패로 보고 메모리 버킷으로도 한 번 더 차감한다
 * (이중 차감). 더 엄격한 쪽으로 틀어지고 극히 드물어서 그대로 둔다.
 */
@Component
class RateLimitCounterAdapter(
    dataSource: DataSource,
    private val clock: Clock,
) : RateLimitCounterPort {

    @Autowired
    constructor(dataSource: DataSource) : this(dataSource, Clock.system(KST))

    private val jdbc = JdbcTemplate(dataSource).apply { queryTimeout = QUERY_TIMEOUT_SECONDS }

    override fun tryConsume(scope: String, key: String, capacity: Int): LocalDate? {
        if (capacity <= 0) return null
        val hashedKey = hash(key)
        val today = today()

        if (increment(scope, hashedKey, today, capacity)) return today
        val inserted = try {
            jdbc.update(INSERT_FIRST_USE, scope, hashedKey, today, scope, hashedKey, today)
        } catch (e: DuplicateKeyException) {
            // 다른 인스턴스가 같은 순간 첫 행을 만들었다(PK 충돌). 아래에서 증가 경로로 다시 간다.
            0
        }
        if (inserted == 1) return today
        // INSERT 가 0행(NOT EXISTS 가 방금 커밋된 타 요청의 행을 봄)이거나 PK 충돌이면 행이 이미 있다.
        // 상한 미만인데 거부하면 거짓 429 이므로 증가 경로를 한 번 더 탄다 — 행이 있으니 ① 이 정확히 판정한다.
        // 당일 행은 삭제되지 않으므로(정리는 2일 뒤) 한 번이면 충분하다.
        return if (increment(scope, hashedKey, today, capacity)) today else null
    }

    override fun refund(scope: String, key: String, date: LocalDate) {
        jdbc.update(REFUND, scope, hash(key), date)
    }

    override fun deleteExpired(): Int =
        jdbc.update(DELETE_EXPIRED, today().minusDays(RETENTION_DAYS))

    private fun increment(scope: String, hashedKey: String, today: LocalDate, capacity: Int): Boolean =
        jdbc.update(INCREMENT, scope, hashedKey, today, capacity) == 1

    private fun today(): LocalDate = LocalDate.now(clock.withZone(KST))

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private const val QUERY_TIMEOUT_SECONDS = 1

        /** 지난 날짜 행 보관 일수. `limit_date < 오늘 - 2일` 인 행을 지운다. */
        const val RETENTION_DAYS = 2L

        private const val INCREMENT =
            "UPDATE rate_limit_counter SET used = used + 1, updated_at = NOW() " +
                "WHERE scope = ? AND limit_key = ? AND limit_date = ? AND used < ?"

        private const val INSERT_FIRST_USE =
            "INSERT INTO rate_limit_counter (scope, limit_key, limit_date, used, updated_at) " +
                "SELECT ?, ?, ?, 1, NOW() WHERE NOT EXISTS " +
                "(SELECT 1 FROM rate_limit_counter WHERE scope = ? AND limit_key = ? AND limit_date = ?)"

        private const val REFUND =
            "UPDATE rate_limit_counter SET used = used - 1, updated_at = NOW() " +
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
