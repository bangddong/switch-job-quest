package com.devquest.ai.controller

import com.devquest.core.domain.model.evaluation.BossPackageResult
import com.devquest.core.domain.port.BossPackageEvaluatorPort
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
class BossPackageControllerTest {

    @Mock
    private lateinit var bossPackageEvaluatorPort: BossPackageEvaluatorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(BossPackageController(bossPackageEvaluatorPort)).build()
    }

    @Test
    fun `evaluate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(bossPackageEvaluatorPort.evaluate(any(), any(), any(), any()))
            .thenReturn(BossPackageResult(overallScore = 90, passed = true))

        mockMvc.post("/internal/ai/boss-package/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"resumeContent":"이력서","githubUrl":"https://github.com/x","blogUrl":"https://blog.x","targetPosition":"백엔드"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.overallScore") { value(90) }
        }

        verify(bossPackageEvaluatorPort).evaluate("이력서", "https://github.com/x", "https://blog.x", "백엔드")
    }
}
