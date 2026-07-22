package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.BossPackageResult
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

class BossPackageHttpEvaluatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = BossPackageHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `evaluate - boss-package evaluate 경로로 요청하고 BossPackageResult를 반환한다`() {
        val expected = BossPackageResult(overallScore = 82)
        server.expect(requestTo("http://localhost:8081/internal/ai/boss-package/evaluate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.resumeContent").value("이력서"))
            .andExpect(jsonPath("$.githubUrl").value("https://github.com/dev"))
            .andExpect(jsonPath("$.blogUrl").value("https://blog.dev"))
            .andExpect(jsonPath("$.targetPosition").value("백엔드"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.evaluate("이력서", "https://github.com/dev", "https://blog.dev", "백엔드")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
