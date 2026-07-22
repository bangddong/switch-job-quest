package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.PersonalityEvaluatorPort
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
class PersonalityControllerTest {

    @Mock
    private lateinit var personalityEvaluatorPort: PersonalityEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(PersonalityController(personalityEvaluatorPort)).build()
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(personalityEvaluatorPort.evaluate(any(), any()))
            .thenReturn(AiEvaluationResult(score = 88, passed = true))

        mockMvc.post("/internal/ai/personality/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"장단점은?","answer":"저는..."}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.score") { value(88) }
        }

        verify(personalityEvaluatorPort).evaluate("장단점은?", "저는...")
    }
}
