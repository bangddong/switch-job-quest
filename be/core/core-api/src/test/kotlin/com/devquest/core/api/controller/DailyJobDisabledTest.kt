package com.devquest.core.api.controller

import com.devquest.core.domain.DailyJobService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * `application.yml` 의 `devquest.jobs.trigger-secret` fallback 이 빈 값임을 고정한다(시크릿을 기본값에 넣지 않는다).
 * EKS core-api 처럼 `DEVQUEST_JOB_SECRET` 이 없는 환경은 어떤 헤더로도 503 disabled 에 머문다 — 의도된 동작이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DailyJobDisabledTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var dailyJobService: DailyJobService

    @Test
    fun `비밀 환경변수가 없으면 어떤 헤더로도 503 disabled`() {
        mockMvc.perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", "anything"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.status").value("disabled"))

        verifyNoInteractions(dailyJobService)
    }
}
