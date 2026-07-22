package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.DeveloperClassResult
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

class DeveloperClassHttpEvaluatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = DeveloperClassHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `evaluate - developer-class evaluate 경로로 요청하고 DeveloperClassResult를 반환한다`() {
        val expected = DeveloperClassResult(developerClass = "B")
        server.expect(requestTo("http://localhost:8081/internal/ai/developer-class/evaluate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.skillAssessmentJson").value("{}"))
            .andExpect(jsonPath("$.careerEssayJson").value("{}"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.evaluate("{}", "{}")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
