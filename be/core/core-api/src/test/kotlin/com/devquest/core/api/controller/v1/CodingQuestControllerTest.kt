package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.ApiControllerAdvice
import com.devquest.core.domain.CodingQuestService
import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.model.coding.CodingProblem
import com.devquest.core.domain.model.coding.CodingSubmissionResult
import com.devquest.core.domain.model.coding.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class CodingQuestControllerTest {

    @Mock
    private lateinit var codingQuestService: CodingQuestService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-1", null, emptyList())
        mockMvc = MockMvcBuilders
            .standaloneSetup(CodingQuestController(codingQuestService))
            .setControllerAdvice(ApiControllerAdvice())
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `GET problem - 정상 요청이면 200과 SUCCESS 반환`() {
        val problem = CodingProblem(
            id = 1L,
            title = "제곱 계산",
            difficulty = "EASY",
            language = "JAVA",
            testCases = listOf(TestCase("5", "25"))
        )
        whenever(codingQuestService.generateProblem(any(), any())).thenReturn(problem)

        mockMvc.get("/api/v1/coding/problem?language=JAVA")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data.title") { value("제곱 계산") }
            }
    }

    @Test
    fun `POST submit - 정상 요청이면 200과 passed 반환`() {
        whenever(codingQuestService.submitCode(any(), eq(1L), eq("JAVA"), any()))
            .thenReturn(CodingSubmissionResult(problemId = 1L, passed = true, message = "모든 테스트케이스 통과"))

        mockMvc.post("/api/v1/coding/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"problemId": 1, "language": "JAVA", "userCode": "class Main {}"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.passed") { value(true) }
        }
    }

    @Test
    fun `GET level - 레벨 반환`() {
        whenever(codingQuestService.getLevel(any())).thenReturn(3)

        mockMvc.get("/api/v1/coding/level")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.level") { value(3) }
            }
    }

    @Test
    fun `POST hint - 정상 요청이면 200과 hint 반환`() {
        whenever(codingQuestService.getHint(any(), any(), any(), any()))
            .thenReturn(CodingHint(hint = "문제를 다른 관점으로 바라보세요."))

        mockMvc.post("/api/v1/coding/hint") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"problemId": 1, "title": "두 수의 합", "description": "두 정수를 더하세요", "hintLevel": 1}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.hint") { value("문제를 다른 관점으로 바라보세요.") }
        }
    }

    @Test
    fun `POST hint - hintLevel 누락 시 400 반환`() {
        mockMvc.post("/api/v1/coding/hint") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"problemId": 1, "title": "두 수의 합", "description": "두 정수를 더하세요"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
