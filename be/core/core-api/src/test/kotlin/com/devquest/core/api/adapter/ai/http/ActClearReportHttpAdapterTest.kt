package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.ActClearReportResult
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

class ActClearReportHttpAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = ActClearReportHttpAdapter(builder.build(), objectMapper)

    @Test
    fun `generate - act-clear-report generate 경로로 요청하고 ActClearReportResult를 반환한다`() {
        val expected = ActClearReportResult(actTitle = "Act 클리어 리포트")
        server.expect(requestTo("http://localhost:8081/internal/ai/act-clear-report/generate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.actId").value(1))
            .andExpect(jsonPath("$.actTitle").value("Act 1"))
            .andExpect(jsonPath("$.questScores.quest-1").value(90))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.generate(1, "Act 1", mapOf("quest-1" to 90))

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
