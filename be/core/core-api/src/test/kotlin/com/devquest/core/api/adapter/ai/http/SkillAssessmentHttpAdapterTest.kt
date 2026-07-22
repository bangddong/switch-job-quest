package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.SkillAssessmentResult
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

class SkillAssessmentHttpAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = SkillAssessmentHttpAdapter(builder.build(), objectMapper)

    @Test
    fun `evaluate - skill-assessment evaluate 경로로 요청하고 SkillAssessmentResult를 반환한다`() {
        val expected = SkillAssessmentResult(developerType = "중급")
        server.expect(requestTo("http://localhost:8081/internal/ai/skill-assessment/evaluate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.skills[0]").value("Kotlin"))
            .andExpect(jsonPath("$.targetRole").value("백엔드"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.evaluate(listOf("Kotlin"), "백엔드")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
