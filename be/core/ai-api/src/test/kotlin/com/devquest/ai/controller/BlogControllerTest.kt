package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.BlogEvaluatorPort
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
class BlogControllerTest {

    @Mock
    private lateinit var blogEvaluatorPort: BlogEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(BlogController(blogEvaluatorPort)).build()
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(blogEvaluatorPort.evaluate(any(), any(), any()))
            .thenReturn(AiEvaluationResult(score = 75, passed = true))

        mockMvc.post("/internal/ai/blog/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"techTopic":"Kotlin","title":"코루틴","content":"내용"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.score") { value(75) }
            jsonPath("$.passed") { value(true) }
        }

        verify(blogEvaluatorPort).evaluate("Kotlin", "코루틴", "내용")
    }
}
