package com.devquest.core.api.controller.v1

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.devquest.core.domain.DailyQuestionContentService
import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.core.domain.port.DailyQuestionContentPort
import com.devquest.storage.db.core.DailyQuestionContentRepository
import com.devquest.storage.db.core.adapter.DailyQuestionContentAdapter
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/**
 * 원장 L-26 해소 — 실제 Postgres에서 `GET /api/v1/daily-question` 읽기 경로를 검증한다.
 *
 * 기존 [DailyQuestionOsivIntegrationTest]는 H2 + 단일 순차 요청이라 "충돌 없는 정상 경로가
 * 완주한다"까지만 증명한다. 이 테스트는 두 가지를 실제 Postgres로 증명한다:
 *
 * ① Stage A 회귀 잠금 — 오늘 행이 없을 때 웹 요청이 200 + 뱅크 질문을 반환하고 행이 1개 생성된다.
 *    #387 이전 코드([DailyQuestionService.getTodayQuestion]이 `findToday(...) ?: throw 404`만 했던
 *    버전, 커밋 `4582783`의 부모)로 이 테스트를 돌리면 `ensureTodayQuestionFromBank` 호출 자체가
 *    없어 무조건 404 — 이 테스트가 그 회귀를 잠근다.
 *
 * ② L-26 핵심 주장 — OSIV가 EntityManager를 바인딩한 **웹 요청 스레드**에서 **진짜 Postgres UNIQUE
 *    제약 위반**이 발생했을 때 `DailyQuestionContentService.saveWithUniqueRecovery`의 재조회 복구가
 *    정상 동작하는지 검증한다. Postgres는 트랜잭션 안에서 한 문장이 실패하면 그 트랜잭션 전체가
 *    abort되고 이후 쿼리가 "current transaction is aborted"로 실패한다(H2는 이렇게 동작하지 않는다).
 *    `SimpleJpaRepository.save()`가 자체 `@Transactional`을 가져 abort가 그 안에 갇힌다는 가설을
 *    이 테스트로 검증한다 — 복구 재조회(`findToday`)가 실패 없이 성공해야 가설이 참이다.
 *
 * ⚠️ 경합 재현 방식(정직성 우선, 스레드 타이밍에 기대지 않는 결정적 방식):
 * 프로덕션 코드는 건드리지 않고, 테스트 전용 [RacyDailyQuestionContentPort]로 `DailyQuestionContentPort`
 * 빈을 감싼다. `findQuestionsSince()`(= `findExisting` 재확인 직후, `save()` 직전 마지막 호출)가
 * 호출되면 별도 JDBC 커넥션(autocommit)으로 "동시 요청이 먼저 커밋한 오늘의 행"을 실제로 INSERT해
 * 둔다. 이후 서비스가 시도하는 `save()`는 실제 Postgres UNIQUE 제약을 위반하게 되어, 스레드 두 개를
 * 띄우는 확률적 방식과 달리 매 실행 결정적으로 재현된다.
 *
 * @DynamicPropertySource로 `storage.datasource.core.*`·dialect·ddl-auto를 컨테이너에 맞게
 * 덮어쓴다(db-core.yml의 H2 고정 설정보다 우선순위가 높다). 스키마는 `ddl-auto: create-drop`이
 * 아니라 [Flyway]로 직접 올려 실제 배포와 동일하게 만든다(V10·V11이 질문 뱅크 26개를 시드).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DailyQuestionPostgresConcurrencyTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dailyQuestionContentRepository: DailyQuestionContentRepository

    @Autowired
    private lateinit var racyPort: RacyDailyQuestionContentPort

    @BeforeEach
    fun cleanState() {
        dailyQuestionContentRepository.deleteAll()
    }

    @Test
    fun `오늘 행이 없으면 실제 Postgres에서도 200과 함께 뱅크 질문이 저장된다 (Stage A 회귀 잠금)`() {
        mockMvc.perform(get("/api/v1/daily-question"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.question").isNotEmpty)

        val saved = dailyQuestionContentRepository.findAll()
        assertThat(saved)
            .describedAs("오늘 행이 정확히 1개 생성돼야 한다")
            .hasSize(1)
    }

    @Test
    fun `OSIV 웹 스레드에서 실제 UNIQUE 위반이 발생해도 saveWithUniqueRecovery가 재조회로 복구한다 (L-26)`() {
        val serviceLogger = LoggerFactory.getLogger(DailyQuestionContentService::class.java) as ch.qos.logback.classic.Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        serviceLogger.addAppender(appender)

        racyPort.armRaceOnNextFindQuestionsSince()

        try {
            // 이 요청 안에서: findToday(2회, 모두 null) -> findQuestionsSince(여기서 "동시 요청"이
            // 실제로 행을 커밋) -> save()가 방금 커밋된 행과 UNIQUE 충돌 -> 재조회로 복구.
            mockMvc.perform(get("/api/v1/daily-question"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.question").value(RacyDailyQuestionContentPort.RACE_WINNER_QUESTION))
        } finally {
            serviceLogger.detachAppender(appender)
        }

        val uniqueViolationLogged = appender.list.any { it.formattedMessage.contains("UNIQUE 충돌") }
        assertThat(uniqueViolationLogged)
            .describedAs("saveWithUniqueRecovery의 UNIQUE 충돌 복구 경로(log.warn)가 실제로 타야 한다")
            .isTrue()

        val savedAfterRace = dailyQuestionContentRepository.findAll()
        assertThat(savedAfterRace)
            .describedAs("충돌 후에도 행은 정확히 1개(동시 요청이 먼저 커밋한 행)여야 한다")
            .hasSize(1)
        assertThat(savedAfterRace.first().question).isEqualTo(RacyDailyQuestionContentPort.RACE_WINNER_QUESTION)

        // 후속 요청(경합 없음)도 같은 질문을 반환하고 행이 늘어나지 않는다 — 멱등성까지 확인.
        mockMvc.perform(get("/api/v1/daily-question"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.question").value(RacyDailyQuestionContentPort.RACE_WINNER_QUESTION))

        assertThat(dailyQuestionContentRepository.findAll()).hasSize(1)
    }

    /**
     * 테스트 전용 `@TestConfiguration` — `@TestConfiguration`은 Spring Boot의
     * `TestConfigurationExcludeFilter`에 의해 컴포넌트 스캔(`scanBasePackages=["com.devquest"]`)에서
     * 자동 제외되므로 prod 부팅 경로를 오염시키지 않는다. `@Primary`로 실제 어댑터 대신 이 빈이
     * `DailyQuestionContentPort` 주입 지점(서비스)에 꽂힌다.
     */
    @TestConfiguration
    class RaceConditionTestConfig {
        @Bean
        @Primary
        fun racyDailyQuestionContentPort(
            delegate: DailyQuestionContentAdapter,
            dataSource: DataSource,
        ): RacyDailyQuestionContentPort = RacyDailyQuestionContentPort(delegate, dataSource)
    }

    companion object {
        // RDS(EKS 학습 환경)·FlywayMigrationIntegrationTest와 버전을 맞춘다 — 근거 없이 바꾸지 않는다.
        private const val POSTGRES_IMAGE = "postgres:17.10"

        // 컴패니언 객체 프로퍼티 초기화는 클래스 최초 참조 시 동기적으로 완료된다 — JUnit의 @BeforeAll이나
        // Spring의 @DynamicPropertySource 정적 메서드가 어떤 순서로 호출되든, 그 시점에는 이미 컨테이너
        // 기동 + Flyway 마이그레이션이 끝나 있음을 보장한다(FlywayMigrationIntegrationTest와 달리 여기서는
        // Spring 컨텍스트가 DataSource를 즉시 사용하므로 @BeforeAll 타이밍에 기대면 순서가 불확실하다).
        private val postgres: PostgreSQLContainer = startContainerAndMigrate()

        private fun startContainerAndMigrate(): PostgreSQLContainer {
            val container = try {
                PostgreSQLContainer(POSTGRES_IMAGE).apply { start() }
            } catch (e: Exception) {
                // FlywayMigrationIntegrationTest와 동일한 이유로 명확한 실패로 죽인다 — 조용한 스킵 금지.
                throw IllegalStateException(
                    "Testcontainers가 Postgres 컨테이너를 띄우지 못했습니다. " +
                        "시도한 DOCKER_HOST=${System.getenv("DOCKER_HOST") ?: "(미설정 — 기본 소켓 /var/run/docker.sock 시도)"}. " +
                        "Docker 자체가 실행 중인지, colima 등 비표준 소켓을 쓴다면 DOCKER_HOST가 " +
                        "그 소켓을 가리키는지 확인하세요.",
                    e,
                )
            }
            // db-core.yml의 H2 + ddl-auto: create-drop 대신, 실제 배포와 동일하게 Flyway로 스키마를 올린다.
            // core-api 테스트 클래스패스에는 core-api·db-core 양쪽 마이그레이션이 합쳐져 로드된다
            // (FlywayMigrationIntegrationTest 참고). V11__seed_tech_question_bank_202607.sql은 절대 수정 금지.
            Flyway.configure()
                .dataSource(container.jdbcUrl, container.username, container.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            return container
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            postgres.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrideDatasource(registry: DynamicPropertyRegistry) {
            // @DynamicPropertySource는 테스트 컨텍스트의 다른 모든 프로퍼티 소스보다 우선순위가 높다
            // (Spring 공식 문서) — db-core.yml(classpath:db-core.yml import)의 H2 고정 설정을 덮어쓴다.
            registry.add("storage.datasource.core.driver-class-name") { "org.postgresql.Driver" }
            registry.add("storage.datasource.core.jdbc-url") { postgres.jdbcUrl }
            registry.add("storage.datasource.core.username") { postgres.username }
            registry.add("storage.datasource.core.password") { postgres.password }
            // 스키마는 이미 Flyway가 올렸다 — Hibernate가 DDL을 만들거나 검증하지 않게 한다.
            // (validate로 하면 이 테스트와 무관한 엔티티/마이그레이션 간 사소한 불일치까지 실패 사유가
            // 될 위험이 있어, 이미 FlywayMigrationIntegrationTest가 검증하는 마이그레이션 정확성과
            // 역할을 분리한다.)
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
            registry.add("spring.jpa.properties.hibernate.dialect") { "org.hibernate.dialect.PostgreSQLDialect" }
        }
    }
}

/**
 * L-26 검증 전용 테스트 더블(프로덕션 코드 아님). [DailyQuestionContentPort]를 그대로 위임하되,
 * `findQuestionsSince()` 호출 시점에 — `findExisting`이 이미 null을 확인한 뒤, `save()` 직전 —
 * 별도 JDBC 커넥션으로 "동시 요청이 먼저 커밋한 오늘의 행"을 실제로 INSERT한다. 스레드/타이밍에
 * 기대지 않는 결정적 경합 재현을 위한 것이다.
 */
class RacyDailyQuestionContentPort(
    private val delegate: DailyQuestionContentPort,
    private val dataSource: DataSource,
) : DailyQuestionContentPort {

    private val armed = AtomicBoolean(false)

    fun armRaceOnNextFindQuestionsSince() {
        armed.set(true)
    }

    override fun findToday(mailType: String, date: LocalDate): DailyQuestionContent? =
        delegate.findToday(mailType, date)

    override fun save(content: DailyQuestionContent): DailyQuestionContent =
        delegate.save(content)

    override fun findQuestionsSince(mailType: String, since: LocalDate): List<String> {
        val result = delegate.findQuestionsSince(mailType, since)
        if (armed.compareAndSet(true, false)) {
            insertConcurrentWinnerRow(mailType)
        }
        return result
    }

    private fun insertConcurrentWinnerRow(mailType: String) {
        // 자동커밋 연결 — OSIV가 웹 스레드에 바인딩한 EntityManager/커넥션과 무관하게 즉시 커밋되어,
        // 뒤이은 save()의 INSERT가 실제 Postgres UNIQUE 제약 위반을 맞는다.
        dataSource.connection.use { conn ->
            conn.autoCommit = true
            conn.prepareStatement(
                "INSERT INTO daily_question_content (question_date, mail_type, question, source, category) " +
                    "VALUES (CURRENT_DATE, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setString(1, mailType)
                stmt.setString(2, RACE_WINNER_QUESTION)
                stmt.setString(3, "BANK")
                stmt.setString(4, "race-test")
                stmt.executeUpdate()
            }
        }
    }

    companion object {
        const val RACE_WINNER_QUESTION = "동시 요청이 먼저 저장한 질문 (경합 재현용)"
    }
}
