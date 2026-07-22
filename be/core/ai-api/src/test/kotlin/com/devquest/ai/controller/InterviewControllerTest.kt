package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.InterviewEvaluationResult
import com.devquest.core.domain.port.InterviewEvaluatorPort
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
class InterviewControllerTest {

    @Mock
    private lateinit var interviewEvaluatorPort: InterviewEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(InterviewController(interviewEvaluatorPort)).build()
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(interviewEvaluatorPort.evaluate(any(), any(), any(), any(), any(), any()))
            .thenReturn(InterviewEvaluationResult(score = 90, passed = true))

        mockMvc.post("/internal/ai/interview/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "category": "JVM",
                  "question": "JVM이란?",
                  "answer": "JVM은...",
                  "questionId": "q-1",
                  "techStack": ["Kotlin"],
                  "yearsOfExperience": "5"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.score") { value(90) }
        }

        verify(interviewEvaluatorPort).evaluate("JVM", "JVM이란?", "JVM은...", "q-1", listOf("Kotlin"), "5")
    }

    @Test
    fun `questions - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(interviewEvaluatorPort.generateQuestions(any(), any(), any(), any(), any(), any()))
            .thenReturn(listOf(mapOf("questionId" to "q-1", "question" to "JVM이란?", "category" to "JVM")))

        mockMvc.post("/internal/ai/interview/questions") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "techStack": ["Kotlin"],
                  "targetRole": "백엔드",
                  "yearsOfExperience": "5",
                  "categories": ["JVM"],
                  "personalityCount": 2,
                  "techCount": 3
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].category") { value("JVM") }
        }

        verify(interviewEvaluatorPort).generateQuestions(listOf("Kotlin"), "백엔드", "5", listOf("JVM"), 2, 3)
    }
}
