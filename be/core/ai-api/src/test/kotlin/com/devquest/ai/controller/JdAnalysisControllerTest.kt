package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
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
class JdAnalysisControllerTest {

    @Mock
    private lateinit var jdAnalysisEvaluatorPort: JdAnalysisEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(JdAnalysisController(jdAnalysisEvaluatorPort)).build()
    }

    @Test
    fun `analyze - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(jdAnalysisEvaluatorPort.analyze(any(), any(), any(), any(), any()))
            .thenReturn(JdAnalysisResult(companyName = "카카오", overallMatchScore = 70))

        mockMvc.post("/internal/ai/jd-analysis/analyze") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"companyName":"카카오","jobDescription":"JD 내용","userSkills":["Kotlin"],"userExperiences":["3년 경력"],"resumeContent":"이력서"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.companyName") { value("카카오") }
        }

        verify(jdAnalysisEvaluatorPort).analyze("카카오", "JD 내용", listOf("Kotlin"), listOf("3년 경력"), "이력서")
    }

    @Test
    fun `analyze - resumeContent 필드 생략 시 서버측 기본값(빈 문자열)으로 복원된다`() {
        whenever(jdAnalysisEvaluatorPort.analyze(any(), any(), any(), any(), any()))
            .thenReturn(JdAnalysisResult(companyName = "카카오", overallMatchScore = 70))

        mockMvc.post("/internal/ai/jd-analysis/analyze") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"companyName":"카카오","jobDescription":"JD 내용","userSkills":["Kotlin"],"userExperiences":["3년 경력"]}"""
        }.andExpect {
            status { isOk() }
        }

        verify(jdAnalysisEvaluatorPort).analyze("카카오", "JD 내용", listOf("Kotlin"), listOf("3년 경력"), "")
    }
}
