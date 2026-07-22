package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.InterviewEvaluationResult
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

class InterviewHttpEvaluatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = InterviewHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `evaluate - interview evaluate 경로로 요청하고 InterviewEvaluationResult를 반환한다`() {
        val expected = InterviewEvaluationResult(score = 75, passed = true)
        server.expect(requestTo("http://localhost:8081/internal/ai/interview/evaluate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.category").value("백엔드"))
            .andExpect(jsonPath("$.question").value("질문"))
            .andExpect(jsonPath("$.answer").value("답변"))
            .andExpect(jsonPath("$.questionId").value("q-1"))
            .andExpect(jsonPath("$.techStack[0]").value("Kotlin"))
            .andExpect(jsonPath("$.yearsOfExperience").value("5"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.evaluate("백엔드", "질문", "답변", "q-1", listOf("Kotlin"), "5")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }

    @Test
    fun `generateQuestions - interview questions 경로로 요청하고 List Map을 반환한다`() {
        val expected = listOf(mapOf("question" to "질문1"))
        server.expect(requestTo("http://localhost:8081/internal/ai/interview/questions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.techStack[0]").value("Kotlin"))
            .andExpect(jsonPath("$.targetRole").value("백엔드"))
            .andExpect(jsonPath("$.yearsOfExperience").value("5"))
            .andExpect(jsonPath("$.categories[0]").value("인성"))
            .andExpect(jsonPath("$.personalityCount").value(2))
            .andExpect(jsonPath("$.techCount").value(3))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.generateQuestions(listOf("Kotlin"), "백엔드", "5", listOf("인성"), 2, 3)

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
