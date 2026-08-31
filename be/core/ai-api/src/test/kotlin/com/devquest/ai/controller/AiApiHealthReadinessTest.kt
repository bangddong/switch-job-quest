package com.devquest.ai.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * core-api `HealthReadinessTest`(L-15) / daily-api `DailyHealthReadinessTest`와 같은 계약을
 * ai-api에도 세운다. 단, ai-api는 DataSource가 없어 "DB를 죽여서 대조"하는 시나리오가 성립하지
 * 않는다(application.yml 주석 참고) — 대신 `/health`(liveness, 상수)와
 * `/actuator/health/readiness`가 **둘 다 항상 200**임을 확인하고, readiness 그룹의 실제 멤버십을
 * 직접 단언한다(QA 지적 F-1과 동일 이유 — 상태 응답 관찰만으로는 include 설정 자체를 증명 못함).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AiApiHealthReadinessTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var healthEndpointGroups: HealthEndpointGroups

    @Test
    fun `health와 readiness 둘 다 200을 반환한다`() {
        mockMvc.perform(get("/health")).andExpect(status().isOk)
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk)
    }

    @Test
    fun `readiness 그룹은 ping만 멤버로 포함하고 db는 포함하지 않는다 - include 설정 자체를 직접 단언`() {
        val readinessGroup = requireNotNull(healthEndpointGroups.get("readiness")) {
            "readiness 그룹이 존재하지 않는다 - management.endpoint.health.group.readiness 설정 확인 필요"
        }

        assertThat(readinessGroup.isMember("ping")).isTrue
        assertThat(readinessGroup.isMember("db")).isFalse
    }
}
