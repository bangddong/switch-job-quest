package com.devquest.core.api.support

import com.devquest.core.domain.model.RateLimitScopes
import com.devquest.core.domain.port.RateLimitCounterPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.security.JwtProvider
import com.devquest.storage.db.core.adapter.RateLimitCounterAdapter
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

/**
 * 실제 스프링 컨텍스트(H2 `create-drop`, 엔티티가 만든 `rate_limit_counter`)에서 인터셉터가 **공유 DB 카운터 경로**를
 * 타는지 고정한다. 단위 테스트는 fake 포트이고, "SQL 이 안 돌아서 메모리 대체로 조용히 통과"하는 것을 여기서 잡는다 —
 * 대체 경로였다면 테이블에 행이 없다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SharedCounterWiringTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var counterPort: RateLimitCounterPort

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var techInterviewPort: TechInterviewPort

    private val jdbc by lazy { JdbcTemplate(dataSource) }

    private fun usedOf(scope: String, key: String): Int? =
        jdbc.queryForList(
            "SELECT used FROM rate_limit_counter WHERE scope = ? AND limit_key = ?",
            Int::class.java, scope, RateLimitCounterAdapter.hash(key),
        ).firstOrNull()

    private val explainBody = """{"question":"Q","answer":"A","feedback":"F","userQuestion":"U"}"""

    @Test
    fun `카운터 포트는 JDBC 어댑터로 배선된다`() {
        assertThat(counterPort).isInstanceOf(RateLimitCounterAdapter::class.java)
    }

    @Test
    fun `익명 explain 은 IP 와 전체 상한을 DB 에 센다 - 해시 키로`() {
        whenever(techInterviewPort.explainFollowup(any(), any(), any(), any(), any())).thenReturn("설명")
        val ip = "203.0.113.50"

        mockMvc.perform(
            post("/api/v1/daily-question/explain").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content(explainBody)
        ).andExpect(status().isOk)

        assertThat(usedOf(RateLimitScopes.DAILY_EXPLAIN, ip)).isEqualTo(1)
        assertThat(usedOf(RateLimitScopes.DAILY_EXPLAIN_GLOBAL, RateLimitScopes.GLOBAL_KEY)).isGreaterThanOrEqualTo(1)
        assertThat(jdbc.queryForList("SELECT limit_key FROM rate_limit_counter", String::class.java))
            .describedAs("원문 IP 가 저장되면 안 된다").doesNotContain(ip)
    }

    @Test
    fun `익명 explain 의 IP 판정 경로가 지표로 보인다 - 비밀 미설정이라 legacy`() {
        whenever(techInterviewPort.explainFollowup(any(), any(), any(), any(), any())).thenReturn("설명")
        val before = meterRegistry.counter(ClientIpResolver.METRIC_NAME, "path", "legacy").count()

        mockMvc.perform(
            post("/api/v1/daily-question/explain").header("X-Forwarded-For", "203.0.113.51")
                .contentType(MediaType.APPLICATION_JSON).content(explainBody)
        ).andExpect(status().isOk)

        assertThat(meterRegistry.counter(ClientIpResolver.METRIC_NAME, "path", "legacy").count()).isEqualTo(before + 1)
    }

    @Test
    fun `기술 면접은 로그인 사용자 ID 를 DB 에 센다`() {
        val userId = "github-wiring-user"
        val token = "Bearer ${jwtProvider.generate(userId)}"
        whenever(techInterviewPort.generateQuestions(any())).thenReturn(
            com.devquest.core.domain.model.evaluation.TechInterviewResult(questions = listOf("Q1"))
        )

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/tech-interview/question")
                .param("techStack", "Java").header("Authorization", token)
        ).andExpect(status().isOk)

        assertThat(usedOf(RateLimitScopes.TECH_INTERVIEW, userId)).isEqualTo(1)
    }
}
