package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
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

class InterviewCoachHttpAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = InterviewCoachHttpAdapter(builder.build(), objectMapper)

    @Test
    fun `startSession - interview-coach start 경로로 요청하고 CoachSessionResult를 반환한다`() {
        val expected = CoachSessionResult(jdSummary = "s-1")
        server.expect(requestTo("http://localhost:8081/internal/ai/interview-coach/start"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.jdText").value("JD 본문"))
            .andExpect(jsonPath("$.targetRole").value("백엔드"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.startSession("JD 본문", "백엔드")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }

    @Test
    fun `evaluateAnswer - interview-coach answer 경로로 요청하고 CoachAnswerResult를 반환한다`() {
        val expected = CoachAnswerResult(score = 70)
        server.expect(requestTo("http://localhost:8081/internal/ai/interview-coach/answer"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.question").value("질문"))
            .andExpect(jsonPath("$.answer").value("답변"))
            .andExpect(jsonPath("$.questionIndex").value(1))
            .andExpect(jsonPath("$.totalQuestions").value(5))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.evaluateAnswer("질문", "답변", 1, 5)

        assertThat(result).isEqualTo(expected)
        server.verify()
    }

    @Test
    fun `generateReport - interview-coach report 경로로 요청하고 CoachReportResult를 반환한다`() {
        val expected = CoachReportResult(overallScore = 88)
        val history = CoachAnswerHistory(question = "질문", answer = "답변", feedback = "피드백")
        server.expect(requestTo("http://localhost:8081/internal/ai/interview-coach/report"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.targetRole").value("백엔드"))
            .andExpect(jsonPath("$.jdSummary").value("JD 요약"))
            .andExpect(jsonPath("$.answers[0].question").value("질문"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = adapter.generateReport("백엔드", "JD 요약", listOf(history))

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
