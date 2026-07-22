package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
import com.devquest.core.domain.port.InterviewCoachPort
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
class InterviewCoachControllerTest {

    @Mock
    private lateinit var interviewCoachPort: InterviewCoachPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(InterviewCoachController(interviewCoachPort)).build()
    }

    @Test
    fun `start - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(interviewCoachPort.startSession(any(), any()))
            .thenReturn(CoachSessionResult(jdSummary = "요약"))

        mockMvc.post("/internal/ai/interview-coach/start") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"jdText":"JD 내용","targetRole":"백엔드"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.jdSummary") { value("요약") }
        }

        verify(interviewCoachPort).startSession("JD 내용", "백엔드")
    }

    @Test
    fun `answer - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(interviewCoachPort.evaluateAnswer(any(), any(), any(), any()))
            .thenReturn(CoachAnswerResult(score = 70))

        mockMvc.post("/internal/ai/interview-coach/answer") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"질문","answer":"답변","questionIndex":0,"totalQuestions":5}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.score") { value(70) }
        }

        verify(interviewCoachPort).evaluateAnswer("질문", "답변", 0, 5)
    }

    @Test
    fun `report - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(interviewCoachPort.generateReport(any(), any(), any()))
            .thenReturn(CoachReportResult(overallScore = 88))

        mockMvc.post("/internal/ai/interview-coach/report") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "targetRole": "백엔드",
                  "jdSummary": "요약",
                  "answers": [{"question":"질문","answer":"답변","feedback":"피드백"}]
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.overallScore") { value(88) }
        }

        verify(interviewCoachPort).generateReport(
            "백엔드",
            "요약",
            listOf(CoachAnswerHistory("질문", "답변", "피드백")),
        )
    }
}
