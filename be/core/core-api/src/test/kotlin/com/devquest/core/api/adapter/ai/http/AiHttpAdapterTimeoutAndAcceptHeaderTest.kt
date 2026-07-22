package com.devquest.core.api.adapter.ai.http

import com.devquest.core.api.adapter.ai.http.support.FakeAiApiResponse
import com.devquest.core.api.adapter.ai.http.support.FakeAiApiServer
import com.devquest.core.api.config.buildAiApiRestClient
import com.devquest.core.domain.support.AiEvaluationException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Phase 1 Task 1.4a 함정 (a)·(c) 실측 검증 — `MockRestServiceServer`는 요청 팩토리 자체를 교체해
 * 실제 타임아웃·서버측 Content-Type 협상을 우회하므로, 이 두 가지는 진짜 소켓으로 붙는
 * [FakeAiApiServer]로만 증명할 수 있다.
 */
class AiHttpAdapterTimeoutAndAcceptHeaderTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `읽기 타임아웃보다 응답이 늦으면 AiEvaluationException으로 실패한다 (함정 a)`() {
        FakeAiApiServer(
            respond = { FakeAiApiResponse(body = "{}", delayMillis = 1500) },
        ).use { fake ->
            val restClient = buildAiApiRestClient(
                baseUrl = "http://localhost:${fake.port}",
                connectTimeoutMs = 1000,
                readTimeoutMs = 300, // 서버 지연(1500ms)보다 짧게 설정 — 타임아웃 유발
            )
            val evaluator = BlogHttpEvaluator(restClient, objectMapper)

            assertThatThrownBy { evaluator.evaluate("Kotlin", "제목", "본문") }
                .isInstanceOf(AiEvaluationException::class.java)
        }
    }

    @Test
    fun `읽기 타임아웃이 응답 지연보다 넉넉하면 정상 응답을 받는다 (타임아웃 설정이 정상 호출을 깨지 않음)`() {
        val body = objectMapper.writeValueAsString(
            com.devquest.core.domain.model.evaluation.AiEvaluationResult(score = 91, passed = true),
        )
        FakeAiApiServer(
            respond = { FakeAiApiResponse(body = body, delayMillis = 200) },
        ).use { fake ->
            val restClient = buildAiApiRestClient(
                baseUrl = "http://localhost:${fake.port}",
                connectTimeoutMs = 1000,
                readTimeoutMs = 5000, // 지연(200ms)보다 충분히 넉넉함
            )
            val evaluator = BlogHttpEvaluator(restClient, objectMapper)

            val result = evaluator.evaluate("Kotlin", "제목", "본문")

            assertThat(result.score).isEqualTo(91)
        }
    }

    @Test
    fun `JSON 22개 엔드포인트 - Accept를 강제하지 않아도 application-json 응답을 정상 파싱한다 (함정 c)`() {
        val body = objectMapper.writeValueAsString(
            com.devquest.core.domain.model.evaluation.AiEvaluationResult(score = 77, passed = true),
        )
        withRealContentNegotiation(
            produces = "application/json",
            body = body,
        ) { port ->
            val restClient = buildAiApiRestClient("http://localhost:$port", 1000, 5000)
            val evaluator = BlogHttpEvaluator(restClient, objectMapper)

            val result = evaluator.evaluate("Kotlin", "제목", "본문")

            assertThat(result.score).isEqualTo(77)
        }
    }

    @Test
    fun `text-plain 2개 엔드포인트 - Accept를 강제하지 않아 406 없이 원문을 그대로 받는다 (함정 c 핵심 증명)`() {
        withRealContentNegotiation(
            produces = "text/plain;charset=UTF-8",
            body = "오늘의 질문",
        ) { port ->
            val restClient = buildAiApiRestClient("http://localhost:$port", 1000, 5000)
            val adapter = TechInterviewHttpAdapter(restClient, objectMapper)

            val result = adapter.generateDailyQuestion("Kotlin", emptyList())

            assertThat(result).isEqualTo("오늘의 질문")
        }
    }

    /**
     * 실제 서버측 콘텐츠 협상을 흉내내는 가짜 서버를 띄운다 — 클라이언트가 보낸 `Accept` 헤더가
     * [produces]와 호환되지 않으면 진짜 Spring MVC처럼 406을 반환한다([FakeAiApiServer.isAcceptable]).
     * `.accept(APPLICATION_JSON)`을 균일하게 강제했다면 text/plain 케이스에서 여기서 406이 나
     * 테스트가 실패했을 것이다.
     */
    private fun withRealContentNegotiation(produces: String, body: String, block: (port: Int) -> Unit) {
        FakeAiApiServer(
            respond = { FakeAiApiResponse(contentType = produces, body = body) },
        ).use { fake ->
            block(fake.port)
        }
    }
}
