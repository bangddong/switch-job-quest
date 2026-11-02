package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.ApiControllerAdvice
import com.devquest.core.domain.DailyJobResult
import com.devquest.core.domain.DailyJobService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper

/**
 * `POST /internal/jobs/daily` 계약 테스트. 서버 비밀은 fail-closed 다 — 비어 있으면 어떤 요청도 일하지 못한다.
 * (SecurityConfig 통과 여부는 [com.devquest.core.api.controller.DailyJobSecurityTest] 가 따로 본다.)
 */
@ExtendWith(MockitoExtension::class)
class DailyJobControllerTest {

    @Mock
    private lateinit var dailyJobService: DailyJobService

    private val secret = "test-secret"

    private fun mockMvc(serverSecret: String): MockMvc = MockMvcBuilders
        .standaloneSetup(DailyJobController(dailyJobService, serverSecret, JsonMapper()))
        .setControllerAdvice(ApiControllerAdvice())
        .build()

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = mockMvc(secret)
    }

    @Test
    fun `서버 비밀이 비어 있으면 헤더가 맞아 보여도 503 disabled 이고 작업은 실행되지 않는다`() {
        mockMvc("").perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", ""))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.status").value("disabled"))
        mockMvc("   ").perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", "   "))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.status").value("disabled"))

        verifyNoInteractions(dailyJobService)
    }

    @Test
    fun `헤더가 없으면 401 unauthorized 이고 작업은 실행되지 않는다`() {
        mockMvc.perform(post("/internal/jobs/daily"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value("unauthorized"))

        verifyNoInteractions(dailyJobService)
    }

    @Test
    fun `헤더가 서버 비밀과 다르면 401 unauthorized 이다 - 접두 일치·길이 차이도 거부`() {
        listOf("wrong", "test-secre", "test-secret-extra", "").forEach { bad ->
            mockMvc.perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", bad))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value("unauthorized"))
        }

        verifyNoInteractions(dailyJobService)
    }

    @Test
    fun `인증은 통과했지만 이미 실행 중이면 409 running`() {
        whenever(dailyJobService.runDaily(anyOrNull())).thenReturn(null)

        mockMvc.perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", secret))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value("running"))
    }

    @Test
    fun `정상 실행이면 200 과 done 응답 필드를 돌려준다`() {
        whenever(dailyJobService.runDaily(anyOrNull()))
            .thenReturn(DailyJobResult(mailTargets = 3, mailSent = 2, expiredRateLimitRowsDeleted = 5))

        mockMvc.perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", secret))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("done"))
            .andExpect(jsonPath("$.mailTargets").value(3))
            .andExpect(jsonPath("$.mailSent").value(2))
            .andExpect(jsonPath("$.expiredRateLimitRowsDeleted").value(5))
    }

    @Test
    fun `메일이 꺼져 있거나 대상이 없어도 200 이다`() {
        whenever(dailyJobService.runDaily(anyOrNull()))
            .thenReturn(DailyJobResult(mailTargets = 0, mailSent = 0, expiredRateLimitRowsDeleted = 0))

        mockMvc.perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", secret))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("done"))
            .andExpect(jsonPath("$.mailTargets").value(0))
    }

    @Test
    fun `body 의 scheduledTime 을 서비스에 넘긴다`() {
        whenever(dailyJobService.runDaily(anyOrNull())).thenReturn(DailyJobResult())

        mockMvc.perform(
            post("/internal/jobs/daily")
                .header("X-DevQuest-Job-Auth", secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scheduledTime": 1700000000000}"""),
        ).andExpect(status().isOk)

        verify(dailyJobService).runDaily(eq(1_700_000_000_000L))
    }

    @Test
    fun `body 가 없거나 깨져 있어도 작업은 돌고 scheduledTime 만 null 이다`() {
        whenever(dailyJobService.runDaily(anyOrNull())).thenReturn(DailyJobResult())

        mockMvc.perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", secret))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/internal/jobs/daily")
                .header("X-DevQuest-Job-Auth", secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/internal/jobs/daily")
                .header("X-DevQuest-Job-Auth", secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scheduledTime": "yesterday"}"""),
        ).andExpect(status().isOk)

        verify(dailyJobService, org.mockito.kotlin.times(3)).runDaily(eq(null))
    }

    @Test
    fun `인증 실패 응답은 서버 비밀을 담지 않는다`() {
        mockMvc.perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", "wrong"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().json("""{"status":"unauthorized"}""", true))
    }

    @Test
    fun `작업이 예외로 실패하면 500 이다 - 성공으로 보이지 않는다`() {
        whenever(dailyJobService.runDaily(anyOrNull())).thenThrow(RuntimeException("AI down"))

        mockMvc.perform(post("/internal/jobs/daily").header("X-DevQuest-Job-Auth", secret))
            .andExpect(status().isInternalServerError)
    }
}
