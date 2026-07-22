package com.devquest.ai.controller

import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.Judge0Result
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
class Judge0ControllerTest {

    @Mock
    private lateinit var judge0Port: Judge0Port

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(Judge0Controller(judge0Port)).build()
    }

    @Test
    fun `execute - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(judge0Port.execute(any(), any(), any(), any()))
            .thenReturn(Judge0Result(stdout = "1", passed = true))

        mockMvc.post("/internal/ai/judge0/execute") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sourceCode":"print(1)","languageId":71,"stdin":"","expectedOutput":"1"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.passed") { value(true) }
        }

        verify(judge0Port).execute("print(1)", 71, "", "1")
    }
}
