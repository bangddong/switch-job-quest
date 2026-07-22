package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.SystemDesignEvaluatorPort
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
class SystemDesignControllerTest {

    @Mock
    private lateinit var systemDesignEvaluatorPort: SystemDesignEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(SystemDesignController(systemDesignEvaluatorPort)).build()
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(systemDesignEvaluatorPort.evaluate(any(), any(), any()))
            .thenReturn(AiEvaluationResult(score = 80, passed = true))

        mockMvc.post("/internal/ai/system-design/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"problemStatement":"문제","architectureDescription":"설계","considerations":["확장성"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.score") { value(80) }
        }

        verify(systemDesignEvaluatorPort).evaluate("문제", "설계", listOf("확장성"))
    }
}
