package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.port.Judge0Result
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

class Judge0HttpAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = Judge0HttpAdapter(builder.build(), objectMapper)

    @Test
    fun `execute - judge0 execute 경로로 요청하고 Judge0Result를 반환한다`() {
        val expected = Judge0Result(stdout = "hello", passed = true)
        server.expect(requestTo("http://localhost:8081/internal/ai/judge0/execute"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.sourceCode").value("println(\"hi\")"))
            .andExpect(jsonPath("$.languageId").value(71))
            .andExpect(jsonPath("$.stdin").value(""))
            .andExpect(jsonPath("$.expectedOutput").value("hi"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.execute("println(\"hi\")", 71, "", "hi")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
