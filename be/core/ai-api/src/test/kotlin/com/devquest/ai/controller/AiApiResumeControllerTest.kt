package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.port.ResumeEvaluatorPort
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
class AiApiResumeControllerTest {

    @Mock
    private lateinit var resumeEvaluatorPort: ResumeEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(AiApiResumeController(resumeEvaluatorPort)).build()
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(resumeEvaluatorPort.evaluate(any(), any(), any()))
            .thenReturn(ResumeCheckResult(overallScore = 85))

        mockMvc.post("/internal/ai/resume/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"targetCompany":"카카오","targetJd":"JD","resumeContent":"이력서"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.overallScore") { value(85) }
        }

        verify(resumeEvaluatorPort).evaluate("카카오", "JD", "이력서")
    }
}
