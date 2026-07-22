package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * `BlogHttpEvaluator` 요청 매핑(경로·body 필드)·응답 역직렬화 계약 테스트.
 *
 * `MockRestServiceServer`로 `RestClient`의 요청 팩토리를 가로채 실제 네트워크 없이 URI·body를
 * 검증하고 응답을 스텁한다(레포에 이미 `spring-boot-starter-test`가 모든 서브모듈 testImplementation에
 * 있어 추가 의존성 불필요 — Task 1.4a 결정 근거).
 */
class BlogHttpEvaluatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = BlogHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `evaluate - blog evaluate 경로로 techTopic title content를 보내고 AiEvaluationResult를 반환한다`() {
        val expected = AiEvaluationResult(score = 88, passed = true, grade = "A")
        server.expect(requestTo("http://localhost:8081/internal/ai/blog/evaluate"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.techTopic").value("Kotlin"))
            .andExpect(jsonPath("$.title").value("코루틴 완전정복"))
            .andExpect(jsonPath("$.content").value("본문"))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON))

        val result = evaluator.evaluate("Kotlin", "코루틴 완전정복", "본문")

        assertThat(result).isEqualTo(expected)
        server.verify()
    }
}
