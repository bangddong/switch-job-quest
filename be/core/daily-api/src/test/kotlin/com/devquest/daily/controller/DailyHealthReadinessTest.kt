package com.devquest.daily.controller

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

/**
 * core-api `HealthReadinessTest`(L-15)와 같은 계약을 daily-api에도 세운다.
 *
 * "200을 준다"만 확인하면 `/health`(상수 응답)도 통과해 이 계약을 못 잡는다. 그래서 DB를
 * 실제로 죽여 `/health`(liveness, 상수)와 `/actuator/health/readiness`(DB 상태 반영)가
 * 서로 다르게 반응함을 한 테스트에서 대조한다 — "통과했다고 믿게 만드는 검사"를 피한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    // 다른 풀 컨텍스트 테스트와 캐시 키를 분리해, 이 테스트가 DataSource를 닫아도 다른 테스트의
    // 공유 컨텍스트를 오염시키지 않게 한다(core-api ReadinessDbDownTest와 동일 이유).
    properties = ["spring.application.name=devquest-daily-readiness-db-down-test"],
)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DailyHealthReadinessTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `DB가 죽어도 health는 200을 유지하지만 readiness는 503으로 바뀐다 - 상수와 실제 검증을 대조`() {
        // DB가 살아있을 때: 둘 다 200
        mockMvc.perform(get("/health")).andExpect(status().isOk)
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk)

        (dataSource as HikariDataSource).close()

        // DB가 죽은 뒤: health는 상수라 여전히 200, readiness는 실제 DB를 봐서 503
        mockMvc.perform(get("/health")).andExpect(status().isOk)
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable)
    }
}

/**
 * QA 지적 F-1(core-api HealthReadinessTest 참고)과 동일한 이유로, 상태 응답 관찰만으로는
 * "readiness 그룹의 include 설정이 실제로 db,ping으로 존재한다"를 증명하지 못한다. 여기서는
 * `HealthEndpointGroups` 빈을 직접 조회해 readiness 그룹의 멤버십을 단언한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DailyReadinessGroupMembershipTest {

    @Autowired
    private lateinit var healthEndpointGroups: HealthEndpointGroups

    @Test
    fun `readiness 그룹은 db와 ping만 멤버로 포함한다 - include 설정 자체를 직접 단언`() {
        val readinessGroup = requireNotNull(healthEndpointGroups.get("readiness")) {
            "readiness 그룹이 존재하지 않는다 - management.endpoint.health.group.readiness 설정 확인 필요"
        }

        assertThat(readinessGroup.isMember("db")).isTrue
        assertThat(readinessGroup.isMember("ping")).isTrue
    }
}
