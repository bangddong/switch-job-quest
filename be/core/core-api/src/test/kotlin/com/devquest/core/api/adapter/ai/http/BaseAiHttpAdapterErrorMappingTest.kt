package com.devquest.core.api.adapter.ai.http

import com.devquest.core.api.config.buildAiApiRestClient
import com.devquest.core.domain.support.AiEvaluationException
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient

/**
 * `BaseAiHttpAdapter`의 에러 매핑 정책 회귀 테스트(함정 (d)) — 로직이 공통 부모 클래스에 있으므로
 * 대표 어댑터([BlogHttpEvaluator]) 하나로 검증하면 18개 어댑터 전부에 적용된다.
 */
class BaseAiHttpAdapterErrorMappingTest {

    private val objectMapper = jacksonObjectMapper()
    private val builder = RestClient.builder().baseUrl("http://localhost:8081")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val evaluator = BlogHttpEvaluator(builder.build(), objectMapper)

    @Test
    fun `ai-api가 500과 message 필드를 포함한 Boot 기본 에러 바디를 반환하면 message를 담은 AiEvaluationException을 던진다`() {
        val errorBody = """
            {"timestamp":"2026-07-22T00:00:00.000+00:00","status":500,"error":"Internal Server Error",
             "path":"/internal/ai/blog/evaluate","message":"평가자 호출 중 예외 발생"}
        """.trimIndent()
        server.expect(requestTo("http://localhost:8081/internal/ai/blog/evaluate"))
            .andRespond(withServerError().contentType(MediaType.APPLICATION_JSON).body(errorBody))

        assertThatThrownBy { evaluator.evaluate("Kotlin", "제목", "본문") }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("평가자 호출 중 예외 발생")

        server.verify()
    }

    @Test
    fun `ai-api가 message 필드 없는 에러 바디를 반환하면 상태코드를 포함한 폴백 메시지로 AiEvaluationException을 던진다`() {
        val errorBody = """{"timestamp":"2026-07-22T00:00:00.000+00:00","status":400,"error":"Bad Request","path":"/internal/ai/blog/evaluate"}"""
        server.expect(requestTo("http://localhost:8081/internal/ai/blog/evaluate"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(errorBody))

        assertThatThrownBy { evaluator.evaluate("Kotlin", "제목", "본문") }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("400")

        server.verify()
    }

    @Test
    fun `ai-api에 연결 자체가 실패하면(네트워크 오류) 재시도 없이 AiEvaluationException으로 단일화한다`() {
        // MockRestServiceServer 없이 실제로 열려있지 않은 포트로 요청 — 연결 거부(ConnectException) 유발.
        val unreachableClient = buildAiApiRestClient(
            baseUrl = "http://localhost:1",
            connectTimeoutMs = 500,
            readTimeoutMs = 500,
        )
        val realEvaluator = BlogHttpEvaluator(unreachableClient, objectMapper)

        assertThatThrownBy { realEvaluator.evaluate("Kotlin", "제목", "본문") }
            .isInstanceOf(AiEvaluationException::class.java)
    }

    @Test
    fun `에러 응답이 와도 재시도하지 않고 단 한 번만 호출한다`() {
        val errorBody = """{"status":500,"error":"Internal Server Error","path":"/internal/ai/blog/evaluate"}"""
        server.expect(requestTo("http://localhost:8081/internal/ai/blog/evaluate"))
            .andRespond(withServerError().contentType(MediaType.APPLICATION_JSON).body(errorBody))

        assertThatThrownBy { evaluator.evaluate("Kotlin", "제목", "본문") }
            .isInstanceOf(AiEvaluationException::class.java)

        // MockRestServiceServer는 기대한 호출 횟수(기본 1회)와 다르면 verify()에서 실패한다 —
        // 재시도가 있었다면 "no further requests expected" 오류로 여기서 잡힌다.
        server.verify()
        assertThat(true).isTrue() // 위 verify() 통과 자체가 단일 호출 증거.
    }
}
