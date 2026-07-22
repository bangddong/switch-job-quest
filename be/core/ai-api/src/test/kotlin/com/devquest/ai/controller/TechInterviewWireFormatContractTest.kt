package com.devquest.ai.controller

import com.devquest.core.domain.port.TechInterviewPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * Task 1.1 QA HIGH 후속 — `String` 반환 엔드포인트(`/daily-question`, `/explain-followup`)의 실제
 * wire format을 실측해 회귀 테스트로 고정한다.
 *
 * 실측 방법: `@SpringBootTest(RANDOM_PORT)`로 실제 내장 서버를 띄우고 JDK `HttpClient`로 직접
 * 붙는다(Spring 클라이언트측 메시지 컨버터를 거치지 않아 서버가 실제로 내보내는 바이트를 그대로
 * 관측할 수 있다). `MockMvc`의 `jsonPath("$")`는 json-smart의 permissive 파싱 때문에 따옴표 없는
 * bare word도 문자열로 통과시켜 이 문제를 못 잡는다 — 그래서 여기서는 원문 바이트를 직접 비교한다.
 *
 * 확정된 wire 계약 (Task 1.4 RestClient 어댑터가 따라야 할 기준):
 * - `POST /internal/ai/tech-interview/daily-question`, `/explain-followup`
 *   → `Content-Type: text/plain;charset=UTF-8`, 바디는 따옴표 없는 raw text, UTF-8 인코딩 정상
 *     (RestClient는 이 두 엔드포인트를 `String`으로 그대로 읽으면 되고 별도 JSON 역직렬화 불필요)
 * - 포트가 런타임 예외를 던지면 Spring Boot 기본 에러 핸들링(`BasicErrorController`)이 동작해
 *   `Content-Type: application/json`, `{"timestamp","status","error","path"}` 형태의 바디를 반환한다
 *   (RFC 7807 `ProblemDetail`이 아니다 — Task 1.4가 이 형태를 core 예외로 매핑해야 한다)
 * - Kotlin non-null 필드가 요청 JSON에서 누락되면 `HttpMessageNotReadableException` → 400,
 *   동일한 `{"timestamp","status","error","path"}` 형태로 응답한다
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TechInterviewWireFormatContractTest {

    @LocalServerPort
    private var port: Int = 0

    @MockitoBean
    private lateinit var techInterviewPort: TechInterviewPort

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `daily-question - 응답 바디는 따옴표 없는 raw text이고 Content-Type은 text-plain UTF-8이다`() {
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("오늘의 질문")

        val response = post("/internal/ai/tech-interview/daily-question", """{"techStack":"Kotlin"}""")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Content-Type").orElse(null))
            .isEqualTo("text/plain;charset=UTF-8")
        // 따옴표 없는 raw text 확정 — JSON 문자열이었다면 "\"오늘의 질문\"" 이어야 한다.
        assertThat(response.body()).isEqualTo("오늘의 질문".toByteArray(StandardCharsets.UTF_8))
    }

    @Test
    fun `explain-followup - 응답 바디는 따옴표 없는 raw text이고 Content-Type은 text-plain UTF-8이다`() {
        whenever(techInterviewPort.explainFollowup(any(), any(), any(), any(), anyOrNull()))
            .thenReturn("설명입니다")

        val response = post(
            "/internal/ai/tech-interview/explain-followup",
            """{"question":"질문","answer":"답변","feedback":"피드백","userQuestion":"추가질문"}""",
        )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Content-Type").orElse(null))
            .isEqualTo("text/plain;charset=UTF-8")
        assertThat(response.body()).isEqualTo("설명입니다".toByteArray(StandardCharsets.UTF_8))
    }

    @Test
    fun `evaluate - 포트가 런타임 예외를 던지면 Boot 기본 에러 응답(500, JSON 바디)을 반환한다`() {
        whenever(techInterviewPort.evaluate(any(), any(), any()))
            .thenThrow(RuntimeException("evaluator boom"))

        val response = post(
            "/internal/ai/tech-interview/evaluate",
            """{"techStack":"Kotlin","questions":["q1"],"answers":["a1"]}""",
        )
        val body = String(response.body(), StandardCharsets.UTF_8)

        assertThat(response.statusCode()).isEqualTo(500)
        assertThat(response.headers().firstValue("Content-Type").orElse(null)).isEqualTo("application/json")
        assertThat(body).contains(""""status":500""")
        assertThat(body).contains(""""error":"Internal Server Error"""")
        assertThat(body).contains(""""path":"/internal/ai/tech-interview/evaluate"""")
    }

    @Test
    fun `questions - 필수 필드(techStack) 누락 시 400과 Bad Request 바디를 반환한다`() {
        val response = post("/internal/ai/tech-interview/questions", "{}")
        val body = String(response.body(), StandardCharsets.UTF_8)

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(body).contains(""""status":400""")
        assertThat(body).contains(""""error":"Bad Request"""")
    }

    private fun post(path: String, body: String): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
    }
}
