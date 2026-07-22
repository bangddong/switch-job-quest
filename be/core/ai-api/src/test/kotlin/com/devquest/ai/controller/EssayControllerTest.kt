package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.EssayCheckResult
import com.devquest.core.domain.port.EssayEvaluatorPort
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
class EssayControllerTest {

    @Mock
    private lateinit var essayEvaluatorPort: EssayEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(EssayController(essayEvaluatorPort)).build()
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(essayEvaluatorPort.evaluate(any(), any(), any()))
            .thenReturn(EssayCheckResult(score = 80, passed = true))

        mockMvc.post("/internal/ai/essay/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "dissatisfactions": ["불만1"],
                  "goals": ["목표1"],
                  "fiveYearVision": "5년 후 CTO"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.score") { value(80) }
            jsonPath("$.passed") { value(true) }
        }

        verify(essayEvaluatorPort).evaluate(listOf("불만1"), listOf("목표1"), "5년 후 CTO")
    }
}
