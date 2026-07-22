package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.ActClearReportResult
import com.devquest.core.domain.port.ActClearReportPort
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
class ActClearReportControllerTest {

    @Mock
    private lateinit var actClearReportPort: ActClearReportPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ActClearReportController(actClearReportPort)).build()
    }

    @Test
    fun `generate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(actClearReportPort.generate(any(), any(), any()))
            .thenReturn(ActClearReportResult(actId = 1, grade = "A"))

        mockMvc.post("/internal/ai/act-clear-report/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"actId":1,"actTitle":"1막","questScores":{"1-1":80}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.grade") { value("A") }
        }

        verify(actClearReportPort).generate(1, "1막", mapOf("1-1" to 80))
    }
}
