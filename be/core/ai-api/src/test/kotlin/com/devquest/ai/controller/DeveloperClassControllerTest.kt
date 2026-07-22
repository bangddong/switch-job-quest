package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.DeveloperClassResult
import com.devquest.core.domain.port.DeveloperClassEvaluatorPort
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
class DeveloperClassControllerTest {

    @Mock
    private lateinit var developerClassEvaluatorPort: DeveloperClassEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(DeveloperClassController(developerClassEvaluatorPort)).build()
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(developerClassEvaluatorPort.evaluate(any(), any()))
            .thenReturn(DeveloperClassResult(overallScore = 90, developerClass = "아키텍트"))

        mockMvc.post("/internal/ai/developer-class/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"skillAssessmentJson":"{}","careerEssayJson":"{}"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.developerClass") { value("아키텍트") }
        }

        verify(developerClassEvaluatorPort).evaluate("{}", "{}")
    }
}
