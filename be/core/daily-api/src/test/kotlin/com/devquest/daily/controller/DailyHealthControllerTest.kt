package com.devquest.daily.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * daily-api `/health`(liveness) — core-api `HealthController`와 동일하게 **상수만 반환**한다.
 * DB 등 실제 의존성 검증은 `/actuator/health/readiness`가 맡는다([DailyHealthReadinessTest] 참고).
 * 이 유닛 테스트는 컨트롤러 계약(경로·응답 봉투)만 확인 — 상수임을 반증하는 검증은
 * 실제 Spring 컨텍스트에서 DB를 죽여야 하므로 [DailyHealthReadinessTest]가 담당한다.
 */
class DailyHealthControllerTest {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(HealthController()).build()
    }

    @Test
    fun `GET health - daily-api 응답 봉투로 200을 반환한다`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data").isString)
    }

}
