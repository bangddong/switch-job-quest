package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * `TechInterviewHttpAdapter` 요청 매핑 계약 테스트. `daily-question`·`explain-followup`은
 * `text/plain;charset=UTF-8` 응답을 그대로 문자열로 받아야 한다(계획 문서 함정 (c) — 406 회피는
 * `AiHttpAdapterTimeoutAndAcceptHeaderTest`에서 실제 서버로 별도 증명).
 */
class TechInterviewHttpAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = TechInterviewHttpAdapter(builder.build(), objectMapper)

    @Test
    fun `generateQuestions - tech-interview questions 경로로 요청하고 TechInterviewResult를 반환한다`() {
        val expected = TechInterviewResult(overallScore = 80, feedback = "피드백")
        server.expect(requestTo("http://localhost:8081/internal/ai/tech-interview/questions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.techStack").value("Kotlin"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.generateQuestions("Kotlin")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }

    @Test
    fun `evaluate - tech-interview evaluate 경로로 요청하고 TechInterviewResult를 반환한다`() {
        val expected = TechInterviewResult(overallScore = 80, feedback = "피드백")
        server.expect(requestTo("http://localhost:8081/internal/ai/tech-interview/evaluate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.techStack").value("Kotlin"))
            .andExpect(jsonPath("$.questions[0]").value("q1"))
            .andExpect(jsonPath("$.answers[0]").value("a1"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.evaluate("Kotlin", listOf("q1"), listOf("a1"))

        assertThat(result).isEqualTo(expected)
        server.verify()
    }

    @Test
    fun `generateDailyQuestion - daily-question 경로로 요청하고 text-plain 원문을 그대로 반환한다`() {
        server.expect(requestTo("http://localhost:8081/internal/ai/tech-interview/daily-question"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.techStack").value("Kotlin"))
            .andExpect(jsonPath("$.recentQuestions[0]").value("이전질문"))
            .andRespond(
                withSuccess("오늘의 질문", MediaType.valueOf("text/plain;charset=UTF-8")),
            )

        val result = adapter.generateDailyQuestion("Kotlin", listOf("이전질문"))

        assertThat(result).isEqualTo("오늘의 질문")
        server.verify()
    }

    @Test
    fun `explainFollowup - explain-followup 경로로 요청하고 text-plain 원문을 그대로 반환한다 (modelAnswer null 포함)`() {
        server.expect(requestTo("http://localhost:8081/internal/ai/tech-interview/explain-followup"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.question").value("질문"))
            .andExpect(jsonPath("$.answer").value("답변"))
            .andExpect(jsonPath("$.feedback").value("피드백"))
            .andExpect(jsonPath("$.userQuestion").value("추가질문"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"modelAnswer\":null")))
            .andRespond(
                withSuccess("설명입니다", MediaType.valueOf("text/plain;charset=UTF-8")),
            )

        val result = adapter.explainFollowup("질문", "답변", "피드백", "추가질문", null)

        assertThat(result).isEqualTo("설명입니다")
        server.verify()
    }
}
