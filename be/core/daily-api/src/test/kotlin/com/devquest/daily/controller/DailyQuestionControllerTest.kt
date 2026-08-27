package com.devquest.daily.controller

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.daily.service.DailyQuestionApiService
import com.devquest.daily.support.DailyQuestionNotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * `AuthenticationPrincipal`을 쓰지 않는다 — daily-api의 3개 엔드포인트는 모두 무로그인 공개 경로
 * (core-api `DailyQuestionController`와 동일 계약).
 */
@ExtendWith(MockitoExtension::class)
class DailyQuestionControllerTest {

    @Mock
    private lateinit var dailyQuestionApiService: DailyQuestionApiService

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(DailyQuestionController(dailyQuestionApiService))
            .setControllerAdvice(DailyApiControllerAdvice())
            .build()
    }

    @Test
    fun `GET daily-question - 오늘의 질문이 있으면 200과 질문을 반환한다`() {
        whenever(dailyQuestionApiService.getTodayQuestion()).thenReturn("Java의 GC 동작 방식을 설명하세요.")

        mockMvc.perform(get("/api/v1/daily-question"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.question").value("Java의 GC 동작 방식을 설명하세요."))
    }

    @Test
    fun `GET daily-question - 뱅크가 소진이면 404를 반환한다`() {
        whenever(dailyQuestionApiService.getTodayQuestion()).thenThrow(DailyQuestionNotFoundException())

        mockMvc.perform(get("/api/v1/daily-question"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("DAILY_QUESTION_NOT_FOUND"))
    }

    @Test
    fun `POST evaluate - 정상 요청이면 200과 평가 결과를 반환한다`() {
        val expected = TechInterviewResult(overallScore = 85, passed = true)
        whenever(dailyQuestionApiService.evaluate(eq("Q"), eq("A"))).thenReturn(expected)

        mockMvc.perform(
            post("/api/v1/daily-question/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("question" to "Q", "answer" to "A")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.overallScore").value(85))
    }

    @Test
    fun `POST evaluate - answer가 빈 문자열이면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/daily-question/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("question" to "Q", "answer" to "")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))

        org.mockito.kotlin.verify(dailyQuestionApiService, org.mockito.kotlin.never()).evaluate(any(), any())
    }

    @Test
    fun `POST explain - 정상 요청이면 200과 설명을 반환한다`() {
        whenever(
            dailyQuestionApiService.explain(eq("Q"), eq("A"), eq("F"), eq("UQ"), eq(null))
        ).thenReturn("설명입니다.")

        mockMvc.perform(
            post("/api/v1/daily-question/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("question" to "Q", "answer" to "A", "feedback" to "F", "userQuestion" to "UQ")
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.explanation").value("설명입니다."))
    }
}
