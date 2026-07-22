package com.devquest.ai.controller

import com.devquest.core.domain.model.coding.CodingProblemGenerationResult
import com.devquest.core.domain.port.CodingProblemGeneratorPort
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
class CodingProblemControllerTest {

    @Mock
    private lateinit var codingProblemGeneratorPort: CodingProblemGeneratorPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(CodingProblemController(codingProblemGeneratorPort)).build()
    }

    @Test
    fun `generate - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(codingProblemGeneratorPort.generate(any(), any(), any()))
            .thenReturn(CodingProblemGenerationResult(title = "두 수의 합"))

        mockMvc.post("/internal/ai/coding-problem/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"difficulty":"EASY","language":"kotlin","category":"array"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("두 수의 합") }
        }

        verify(codingProblemGeneratorPort).generate("EASY", "kotlin", "array")
    }
}
