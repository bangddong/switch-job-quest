package com.devquest.core.api.controller

import com.devquest.core.domain.DailyJobResult
import com.devquest.core.domain.DailyJobService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 실제 SecurityConfig 를 통과시켜 `/internal/jobs` 하위 경로가 JWT 없이 도달하고(permitAll) POST 가 CSRF 에 막히지 않음을,
 * 그리고 인증은 오직 `X-DevQuest-Job-Auth` 헤더가 한다는 것을 고정한다. `/api` 밖 경로라 Worker 가 비밀 헤더를 대신 붙여주지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["devquest.jobs.trigger-secret=test-secret"])
class DailyJobSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var dailyJobService: DailyJobService

    @Test
    fun `JWT 없이 헤더만으로 도달해 200 이 된다 - CSRF 도 POST 를 막지 않는다`() {
        whenever(dailyJobService.runDaily(anyOrNull()))
            .thenReturn(DailyJobResult(mailTargets = 1, mailSent = 1, expiredRateLimitRowsDeleted = 0))

        mockMvc.perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", "test-secret"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("done"))
    }

    @Test
    fun `헤더가 없으면 Security 의 403 이 아니라 컨트롤러의 401 unauthorized 이다`() {
        mockMvc.perform(post("/internal/jobs/daily"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value("unauthorized"))

        verifyNoInteractions(dailyJobService)
    }
}
