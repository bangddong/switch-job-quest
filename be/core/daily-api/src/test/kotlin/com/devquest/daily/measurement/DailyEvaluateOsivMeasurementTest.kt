package com.devquest.daily.measurement

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * D-016 DB 타임아웃 결정 전 실측 - M3 (daily-api 오늘의 질문 채점).
 *
 * `POST /api/v1/daily-question/evaluate` 가 AI 포트(`TechInterviewPort.evaluate`, 프로덕션에서는 ai-api HTTP 호출로
 * 최대 150초)를 기다리는 동안 HikariCP 커넥션을 쥐는가. 실제 DispatcherServlet · OSIV 인터셉터 · 레이트 리밋 인터셉터 ·
 * 컨트롤러 · 서비스 체인을 MockMvc 로 통과시키고 AI 포트만 [BlockingGate] 로 블록한다.
 *
 * 구성별로 `spring.jpa.open-in-view` 만 다르다(daily-api 설정에도 이 키는 0건 → 기본 true).
 * DB 는 H2 - 이 경로는 요청 스레드에서 JPA 를 쓰지 않으므로(레이트 리밋은 JdbcTemplate, 문장마다 빌렸다 반환) DB 종류와 무관하다.
 */
abstract class AbstractDailyEvaluateMeasurement(
    private val config: String,
    private val osivInterceptorBeans: Int,
) {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var dataSource: DataSource

    @Autowired
    protected lateinit var context: ApplicationContext

    @MockitoBean
    protected lateinit var techInterviewPort: TechInterviewPort

    private lateinit var probe: HikariProbe

    @BeforeEach
    fun setUp() {
        probe = HikariProbe(dataSource)
    }

    @Test
    fun `OSIV 인터셉터 빈 존재가 구성과 일치한다 (설정이 컨텍스트에 실제로 반영됐는지)`() {
        assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java)).hasSize(osivInterceptorBeans)
    }

    @Test
    fun `M3 데일리 질문 채점 - AI 포트 블록 중 active 커넥션`() {
        val gate = BlockingGate()
        whenever(techInterviewPort.evaluate(any(), any(), any())).thenAnswer {
            gate.block()
            TechInterviewResult(overallScore = 80, passed = true)
        }
        // IP 별 한도(1회/일)에 걸리지 않도록 테스트마다 다른 IP 를 쓴다.
        val ip = "198.51.100.${IP_SEQ.incrementAndGet()}"

        val m = measureWhileBlocked(probe, gate) {
            mockMvc.perform(
                post("/api/v1/daily-question/evaluate").header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"question":"Q","answer":"A"}""")
            ).andReturn().response.status
        }
        println(
            "[MEASURE] M3(daily evaluate@AI) config=$config " +
                "osivInterceptorBeans=${context.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java).size} " +
                "poolMax=${probe.maximumPoolSize} samples=${m.samples.map { it.active }} last={${m.samples.last()}} status=${m.result}"
        )

        assertThat(m.result).describedAs("요청은 블록 해제 후 성공해야 한다").isEqualTo(200)
        assertThat(m.samples.map { it.active }).describedAs("AI 블록 중 active").containsOnly(0)
    }

    companion object {
        private val IP_SEQ = AtomicInteger(10)
    }
}

/** 구성 A - 현재 설정(OSIV 기본 true). */
@SpringBootTest
@AutoConfigureMockMvc
class DailyEvaluateOsivDefaultMeasurementTest : AbstractDailyEvaluateMeasurement(
    config = "A:osiv-default(H2)",
    osivInterceptorBeans = 1,
)

/** 구성 B - `spring.jpa.open-in-view=false` (테스트 프로퍼티로만). */
@SpringBootTest(properties = ["spring.jpa.open-in-view=false"])
@AutoConfigureMockMvc
class DailyEvaluateOsivDisabledMeasurementTest : AbstractDailyEvaluateMeasurement(
    config = "B:osiv-false(H2)",
    osivInterceptorBeans = 0,
)
