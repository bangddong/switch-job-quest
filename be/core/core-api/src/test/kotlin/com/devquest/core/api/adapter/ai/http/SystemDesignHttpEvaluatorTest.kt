package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
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

class SystemDesignHttpEvaluatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = SystemDesignHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `evaluate - system-design evaluate 경로로 요청하고 AiEvaluationResult를 반환한다`() {
        val expected = AiEvaluationResult(score = 55, passed = false)
        server.expect(requestTo("http://localhost:8081/internal/ai/system-design/evaluate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.problemStatement").value("문제"))
            .andExpect(jsonPath("$.architectureDescription").value("설계"))
            .andExpect(jsonPath("$.considerations[0]").value("고려사항1"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.evaluate("문제", "설계", listOf("고려사항1"))

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
