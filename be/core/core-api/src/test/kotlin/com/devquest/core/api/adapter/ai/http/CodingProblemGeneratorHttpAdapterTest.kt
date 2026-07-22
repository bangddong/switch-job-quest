package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.coding.CodingProblemGenerationResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

class CodingProblemGeneratorHttpAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = CodingProblemGeneratorHttpAdapter(builder.build(), objectMapper)

    @Test
    fun `generate - coding-problem generate 경로로 요청하고 CodingProblemGenerationResult를 반환한다`() {
        val expected = CodingProblemGenerationResult(title = "투 포인터 문제")
        server.expect(requestTo("http://localhost:8081/internal/ai/coding-problem/generate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
            .andExpect(jsonPath("$.language").value("Kotlin"))
            .andExpect(jsonPath("$.category").value("배열"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.generate("MEDIUM", "Kotlin", "배열")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
