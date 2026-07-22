package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.JourneyReportResult
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

class JourneyReportHttpAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = JourneyReportHttpAdapter(builder.build(), objectMapper)

    @Test
    fun `generate - journey-report generate 경로로 요청하고 JourneyReportResult를 반환한다`() {
        val expected = JourneyReportResult(narrative = "여정 리포트")
        server.expect(requestTo("http://localhost:8081/internal/ai/journey-report/generate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.companyName").value("토스"))
            .andExpect(jsonPath("$.targetPosition").value("백엔드"))
            .andExpect(jsonPath("$.questScores.quest-1").value(80))
            .andExpect(jsonPath("$.totalXp").value(1200))
            .andExpect(jsonPath("$.completedQuestCount").value(5))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.generate("토스", "백엔드", mapOf("quest-1" to 80), 1200, 5)

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
