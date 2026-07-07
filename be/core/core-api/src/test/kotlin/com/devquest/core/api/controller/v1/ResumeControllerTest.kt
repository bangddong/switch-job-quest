package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.ApiControllerAdvice
import com.devquest.core.domain.ResumeService
import com.devquest.core.domain.model.UserResume
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class ResumeControllerTest {

    @Mock
    private lateinit var resumeService: ResumeService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-1", null, emptyList())
        mockMvc = MockMvcBuilders
            .standaloneSetup(ResumeController(resumeService))
            .setControllerAdvice(ApiControllerAdvice())
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `GET resume - 등록된 이력서가 있으면 200과 데이터 반환`() {
        whenever(resumeService.getResume("user-1")).thenReturn(
            UserResume(id = 1L, userId = "user-1", content = "5년차 백엔드 개발자")
        )

        mockMvc.get("/api/v1/resume")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data.content") { value("5년차 백엔드 개발자") }
            }
    }

    @Test
    fun `GET resume - 등록된 이력서가 없으면 200과 null 데이터 반환`() {
        whenever(resumeService.getResume("user-1")).thenReturn(null)

        mockMvc.get("/api/v1/resume")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data") { value(org.hamcrest.Matchers.nullValue()) }
            }
    }

    @Test
    fun `PUT resume - 정상 요청이면 200과 저장된 이력서 반환`() {
        whenever(resumeService.saveResume("user-1", "새 이력서 내용")).thenReturn(
            UserResume(id = 1L, userId = "user-1", content = "새 이력서 내용")
        )

        mockMvc.put("/api/v1/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"새 이력서 내용"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.content") { value("새 이력서 내용") }
        }

        verify(resumeService).saveResume("user-1", "새 이력서 내용")
    }

    @Test
    fun `PUT resume - content가 비어있으면 400 반환`() {
        mockMvc.put("/api/v1/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.result") { value("ERROR") }
        }
    }
}
