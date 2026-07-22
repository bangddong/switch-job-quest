package com.devquest.ai.controller

import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.port.CodingHintPort
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
class CodingHintControllerTest {

    @Mock
    private lateinit var codingHintPort: CodingHintPort

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(CodingHintController(codingHintPort)).build()
    }

    @Test
    fun `getHint - 요청을 포트에 위임하고 결과를 그대로 반환한다`() {
        whenever(codingHintPort.getHint(any(), any(), any(), any()))
            .thenReturn(CodingHint(hint = "투 포인터를 써보세요"))

        mockMvc.post("/internal/ai/coding-hint/get") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"problemId":1,"title":"두 수의 합","description":"설명","hintLevel":1}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.hint") { value("투 포인터를 써보세요") }
        }

        verify(codingHintPort).getHint(1L, "두 수의 합", "설명", 1)
    }
}
