package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.JourneyReportResult
import com.devquest.core.domain.port.JourneyReportPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class JourneyReportControllerTest {

    @Mock
    private lateinit var journeyReportPort: JourneyReportPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(JourneyReportController(journeyReportPort)).build()
    }

    @Test
    fun `generate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(journeyReportPort.generate(any(), any(), any(), any(), any()))
            .thenReturn(JourneyReportResult(companyName = "카카오", totalXp = 1000))

        mockMvc.post("/internal/ai/journey-report/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"companyName":"카카오","targetPosition":"백엔드","questScores":{"1-1":80},"totalXp":1000,"completedQuestCount":5}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalXp") { value(1000) }
        }

        verify(journeyReportPort).generate("카카오", "백엔드", mapOf("1-1" to 80), 1000, 5)
    }
}
