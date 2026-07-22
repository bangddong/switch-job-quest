package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.JdAnalysisResult
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

class JdAnalysisHttpEvaluatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = JdAnalysisHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `analyze - jd-analysis analyze 경로로 요청하고 JdAnalysisResult를 반환한다`() {
        val expected = JdAnalysisResult(overallMatchScore = 65)
        server.expect(requestTo("http://localhost:8081/internal/ai/jd-analysis/analyze"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.companyName").value("토스"))
            .andExpect(jsonPath("$.jobDescription").value("JD 본문"))
            .andExpect(jsonPath("$.userSkills[0]").value("Kotlin"))
            .andExpect(jsonPath("$.userExperiences[0]").value("경력1"))
            .andExpect(jsonPath("$.resumeContent").value("이력서"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.analyze("토스", "JD 본문", listOf("Kotlin"), listOf("경력1"), "이력서")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
