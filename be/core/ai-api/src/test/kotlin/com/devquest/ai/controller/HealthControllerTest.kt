package com.devquest.ai.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * ai-api `/health`(liveness) — 상수만 반환한다. 실제 의존성 검증은 `/actuator/health/readiness`가
 * 맡는다([AiApiHealthReadinessTest] 참고). 이 유닛 테스트는 컨트롤러 계약(경로·상태코드·본문 형식)만
 * 확인한다.
 */
class HealthControllerTest {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(HealthController()).build()
    }

    @Test
    fun `GET health - 200과 본문을 반환한다`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
    }
}
