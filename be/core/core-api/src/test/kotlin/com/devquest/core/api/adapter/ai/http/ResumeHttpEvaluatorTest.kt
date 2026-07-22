package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.ResumeCheckResult
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

class ResumeHttpEvaluatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = ResumeHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `evaluate - resume evaluate 경로로 요청하고 ResumeCheckResult를 반환한다`() {
        val expected = ResumeCheckResult(overallScore = 70, passed = true)
        server.expect(requestTo("http://localhost:8081/internal/ai/resume/evaluate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.targetCompany").value("네이버"))
            .andExpect(jsonPath("$.targetJd").value("백엔드"))
            .andExpect(jsonPath("$.resumeContent").value("이력서 본문"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.evaluate("네이버", "백엔드", "이력서 본문")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
