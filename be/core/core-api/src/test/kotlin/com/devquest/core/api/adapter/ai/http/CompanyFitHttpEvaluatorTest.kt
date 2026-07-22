package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.CompanyFitResult
import com.devquest.core.domain.port.CompanyInfo
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

class CompanyFitHttpEvaluatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = CompanyFitHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `analyze - company-fit analyze 경로로 요청하고 List CompanyFitResult를 반환한다 (제네릭 소거 방지 검증)`() {
        val expected = listOf(CompanyFitResult(companyName = "카카오", fitScore = 80))
        val company = CompanyInfo(
            name = "카카오",
            culture = "수평적",
            techStack = listOf("Kotlin"),
            size = "대기업",
            description = "설명",
        )
        server.expect(requestTo("http://localhost:8081/internal/ai/company-fit/analyze"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.preferences.culture").value("수평적"))
            .andExpect(jsonPath("$.companies[0].name").value("카카오"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.analyze(mapOf("culture" to "수평적"), listOf(company))

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
