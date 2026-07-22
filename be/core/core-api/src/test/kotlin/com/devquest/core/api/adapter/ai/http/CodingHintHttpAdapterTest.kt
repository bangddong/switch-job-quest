package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.coding.CodingHint
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class CodingHintHttpAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = CodingHintHttpAdapter(builder.build(), objectMapper)

    @Test
    fun `getHint - coding-hint get 경로로 요청하고 CodingHint를 반환한다`() {
        val expected = CodingHint(hint = "투 포인터를 써보세요")
        server.expect(requestTo("http://localhost:8081/internal/ai/coding-hint/get"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.problemId").value(1))
            .andExpect(jsonPath("$.title").value("문제 제목"))
            .andExpect(jsonPath("$.description").value("문제 설명"))
            .andExpect(jsonPath("$.hintLevel").value(1))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.getHint(1L, "문제 제목", "문제 설명", 1)

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
