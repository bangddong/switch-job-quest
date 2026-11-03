package com.devquest.daily.osiv

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
 * B7-21 ⓐ 회귀 - daily-api 오늘의 질문 채점.
 *
 * `POST /api/v1/daily-question/evaluate` 가 AI 포트(`TechInterviewPort.evaluate`, 프로덕션에서는 ai-api HTTP 호출로
 * 최대 150초)를 기다리는 동안 HikariCP 커넥션을 쥐지 않는다. 실제 DispatcherServlet · (OSIV 인터셉터가 있다면 그것) ·
 * 레이트 리밋 인터셉터 · 컨트롤러 · 서비스 체인을 MockMvc 로 통과시키고 AI 포트만 [BlockingGate] 로 블록한다.
 *
 * 🔴 기본 설정 그대로(프로퍼티 오버라이드 없음) 띄운다. 이 경로는 요청 스레드에서 JPA 를 쓰지 않아(레이트 리밋은 JdbcTemplate,
 * 문장마다 빌렸다 반환) OSIV 가 켜져 있어도 active=0 이다(D-016 M3 실측) - 그래서 active 단언만으로는 OSIV 설정을 못 잠근다.
 * "OSIV 는 꺼져 있다" 는 실효 프로퍼티·인터셉터 빈 단언이 맡고, active 단언은 "이 경로가 앞으로도 커넥션을 쥐지 않는다" 를 맡는다.
 * DB 는 H2 - 이 경로는 DB 종류와 무관하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DailyEvaluateConnectionTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var context: ApplicationContext

    @MockitoBean
    private lateinit var techInterviewPort: TechInterviewPort

    private lateinit var probe: HikariProbe

    @BeforeEach
    fun setUp() {
        probe = HikariProbe(dataSource)
    }

    @Test
    fun `OSIV 는 꺼져 있다 - 실효 프로퍼티와 인터셉터 빈이 모두 반영됐다`() {
        assertThat(context.environment.getProperty("spring.jpa.open-in-view"))
            .describedAs("spring.jpa.open-in-view 실효값(어느 yml 에서 왔든 최종 Environment 기준)").isEqualTo("false")
        assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java))
            .describedAs("OSIV 인터셉터 빈").isEmpty()
    }

    @Test
    fun `데일리 질문 채점 - AI 포트 대기 중에는 커넥션을 쥐지 않는다`() {
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
            "[OSIV-REGRESSION] daily evaluate@AI " +
                "osivInterceptorBeans=${context.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java).size} " +
                "poolMax=${probe.maximumPoolSize} samples=${m.samples.map { it.active }} last={${m.samples.last()}} status=${m.result}"
        )

        assertThat(m.result).describedAs("요청은 블록 해제 후 성공해야 한다").isEqualTo(200)
        assertThat(m.samples.map { it.active }).describedAs("AI 대기 중 active").containsOnly(0)
    }

    companion object {
        private val IP_SEQ = AtomicInteger(10)
    }
}
