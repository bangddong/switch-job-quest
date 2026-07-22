package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.EssayCheckResult
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

class EssayHttpEvaluatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = EssayHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `evaluate - essay evaluate 경로로 요청하고 EssayCheckResult를 반환한다`() {
        val expected = EssayCheckResult(score = 60, passed = true)
        server.expect(requestTo("http://localhost:8081/internal/ai/essay/evaluate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.dissatisfactions[0]").value("불만1"))
            .andExpect(jsonPath("$.goals[0]").value("목표1"))
            .andExpect(jsonPath("$.fiveYearVision").value("5년 비전"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.evaluate(listOf("불만1"), listOf("목표1"), "5년 비전")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
