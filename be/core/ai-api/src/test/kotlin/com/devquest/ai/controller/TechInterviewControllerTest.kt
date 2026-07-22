package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class TechInterviewControllerTest {

    @Mock
    private lateinit var techInterviewPort: TechInterviewPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(TechInterviewController(techInterviewPort)).build()
    }

    @Test
    fun `questions - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(techInterviewPort.generateQuestions(any()))
            .thenReturn(TechInterviewResult(questions = listOf("질문1")))

        mockMvc.post("/internal/ai/tech-interview/questions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"techStack":"Kotlin"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.questions[0]") { value("질문1") }
        }

        verify(techInterviewPort).generateQuestions("Kotlin")
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(techInterviewPort.evaluate(any(), any(), any()))
            .thenReturn(TechInterviewResult(overallScore = 80))

        mockMvc.post("/internal/ai/tech-interview/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"techStack":"Kotlin","questions":["질문1"],"answers":["답변1"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.overallScore") { value(80) }
        }

        verify(techInterviewPort).evaluate("Kotlin", listOf("질문1"), listOf("답변1"))
    }

    @Test
    fun `daily-question - recentQuestions 포함 시 그대로 전달된다`() {
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("오늘의 질문")

        mockMvc.post("/internal/ai/tech-interview/daily-question") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"techStack":"Kotlin","recentQuestions":["이전질문1"]}"""
        }.andExpect {
            status { isOk() }
            // 실제 wire 계약(TechInterviewWireFormatContractTest로 실측 고정)은 따옴표 없는 raw
            // text다 — jsonPath("$")는 json-smart의 permissive 파싱 때문에 거짓 통과가 가능해
            // content { string(...) } 원문 비교로 고정한다.
            content { contentType("text/plain;charset=UTF-8") }
            content { string("오늘의 질문") }
        }

        verify(techInterviewPort).generateDailyQuestion("Kotlin", listOf("이전질문1"))
    }

    @Test
    fun `daily-question - recentQuestions 필드 생략 시 서버측 기본값(빈 리스트)으로 복원된다`() {
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("오늘의 질문")

        mockMvc.post("/internal/ai/tech-interview/daily-question") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"techStack":"Kotlin"}"""
        }.andExpect {
            status { isOk() }
        }

        verify(techInterviewPort).generateDailyQuestion(eq("Kotlin"), eq(emptyList()))
    }

    @Test
    fun `explain-followup - modelAnswer 생략 시 null 그대로 전달된다`() {
        whenever(techInterviewPort.explainFollowup(any(), any(), any(), any(), eq(null)))
            .thenReturn("설명")

        mockMvc.post("/internal/ai/tech-interview/explain-followup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"질문","answer":"답변","feedback":"피드백","userQuestion":"추가질문"}"""
        }.andExpect {
            status { isOk() }
        }

        verify(techInterviewPort).explainFollowup("질문", "답변", "피드백", "추가질문", null)
    }
}
