package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.SkillAssessmentResult
import com.devquest.core.domain.port.SkillAssessmentPort
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
class SkillAssessmentControllerTest {

    @Mock
    private lateinit var skillAssessmentPort: SkillAssessmentPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(SkillAssessmentController(skillAssessmentPort)).build()
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(skillAssessmentPort.evaluate(any(), any()))
            .thenReturn(SkillAssessmentResult(score = 80, developerType = "백엔드"))

        mockMvc.post("/internal/ai/skill-assessment/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"skills":["Kotlin","Spring"],"targetRole":"백엔드"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.developerType") { value("백엔드") }
        }

        verify(skillAssessmentPort).evaluate(listOf("Kotlin", "Spring"), "백엔드")
    }
}
