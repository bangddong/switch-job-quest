package com.devquest.daily

import com.devquest.core.domain.model.RateLimitScopes
import com.devquest.core.domain.port.RateLimitCounterPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.daily.support.RateLimitResetScheduler
import com.devquest.storage.db.core.adapter.RateLimitCounterAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

/**
 * 전역 일일 상한의 자정 정리가 **실제로 돌도록** 배선돼 있는지, 그리고 인터셉터가 **공유 DB 카운터 경로**를
 * 타는지 본다(U-6). daily-api 테스트는 전부 H2 라서, SQL 이 안 돌아 메모리 대체로 조용히 통과하는 일이 없도록
 * 요청 뒤 `rate_limit_counter` 에 행이 실제로 생겼는지 확인한다(대체 경로였다면 행이 없다).
 * `@Scheduled` 는 `@EnableScheduling` 이 없으면 조용히 무시되므로(에러 0, 리셋 0) 컨텍스트에서 직접 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DailyApiRateLimitWiringTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dataSource: DataSource

    @MockitoBean
    private lateinit var techInterviewPort: TechInterviewPort

    @Test
    fun `자정 리셋 스케줄러 빈이 있고 스케줄링이 활성화돼 있다`() {
        assertThat(context.getBeansOfType(RateLimitResetScheduler::class.java)).hasSize(1)
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java)).isNotEmpty()
    }

    @Test
    fun `카운터 포트는 JDBC 어댑터로 배선된다`() {
        assertThat(context.getBean(RateLimitCounterPort::class.java)).isInstanceOf(RateLimitCounterAdapter::class.java)
    }

    @Test
    fun `익명 explain 은 IP 와 전체 상한을 DB 에 해시 키로 센다`() {
        whenever(techInterviewPort.explainFollowup(any(), any(), any(), any(), any())).thenReturn("설명")
        val ip = "203.0.113.60"

        mockMvc.perform(
            post("/api/v1/daily-question/explain").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"Q","answer":"A","feedback":"F","userQuestion":"U"}""")
        ).andExpect(status().isOk)

        val jdbc = JdbcTemplate(dataSource)
        fun usedOf(scope: String, key: String) = jdbc.queryForList(
            "SELECT used FROM rate_limit_counter WHERE scope = ? AND limit_key = ?",
            Int::class.java, scope, RateLimitCounterAdapter.hash(key),
        ).firstOrNull()

        assertThat(usedOf(RateLimitScopes.DAILY_EXPLAIN, ip)).isEqualTo(1)
        assertThat(usedOf(RateLimitScopes.DAILY_EXPLAIN_GLOBAL, RateLimitScopes.GLOBAL_KEY)).isGreaterThanOrEqualTo(1)
        assertThat(jdbc.queryForList("SELECT limit_key FROM rate_limit_counter", String::class.java))
            .describedAs("원문 IP 가 저장되면 안 된다").doesNotContain(ip)
    }
}
