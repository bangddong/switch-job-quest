package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.CompanyFitResult
import com.devquest.core.domain.port.CompanyFitEvaluatorPort
import com.devquest.core.domain.port.CompanyInfo
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
class CompanyFitControllerTest {

    @Mock
    private lateinit var companyFitEvaluatorPort: CompanyFitEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(CompanyFitController(companyFitEvaluatorPort)).build()
    }

    @Test
    fun `analyze - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(companyFitEvaluatorPort.analyze(any(), any()))
            .thenReturn(listOf(CompanyFitResult(companyName = "카카오", fitScore = 85)))

        mockMvc.post("/internal/ai/company-fit/analyze") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "preferences": {"culture": "수평적"},
                  "companies": [{"name":"카카오","culture":"수평","techStack":["Kotlin"],"size":"대기업","description":"카카오"}]
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].companyName") { value("카카오") }
        }

        verify(companyFitEvaluatorPort).analyze(
            mapOf("culture" to "수평적"),
            listOf(CompanyInfo("카카오", "수평", listOf("Kotlin"), "대기업", "카카오")),
        )
    }
}
