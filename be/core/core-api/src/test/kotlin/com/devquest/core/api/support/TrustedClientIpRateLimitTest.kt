package com.devquest.core.api.support

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.io.PrintWriter
import java.io.StringWriter

/**
 * B7-19 2단계 — Cloudflare Worker 이그레스가 `Fly-Client-IP` 로 보이는 환경에서
 * 익명 사용자들이 한 버킷을 공유하던 오탐(429)이 신뢰 헤더로 풀리는지 인터셉터 수준에서 검증한다.
 */
class TrustedClientIpRateLimitTest {

    private val secret = "test-edge-secret"
    private val workerEgressIp = "172.68.0.1"

    private lateinit var interceptor: DailyEvaluateRateLimitInterceptor

    @BeforeEach
    fun setUp() {
        interceptor = DailyEvaluateRateLimitInterceptor(
            DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1),
            DailyEvaluateGlobalRateLimitStore(capacity = 1000, refillDays = 1),
            ObjectMapper(),
            memoryOnlyLimiter(),
            ClientIpResolver(secret),
        )
    }

    private fun workerRequest(edgeAuth: String?, edgeClientIp: String?): HttpServletRequest {
        val request = mock<HttpServletRequest>()
        whenever(request.getHeader("Fly-Client-IP")).thenReturn(workerEgressIp)
        whenever(request.getHeader("X-DevQuest-Edge-Auth")).thenReturn(edgeAuth)
        whenever(request.getHeader("X-DevQuest-Client-IP")).thenReturn(edgeClientIp)
        whenever(request.remoteAddr).thenReturn("10.0.0.1")
        return request
    }

    private fun response(): HttpServletResponse {
        val response = mock<HttpServletResponse>()
        whenever(response.writer).thenReturn(PrintWriter(StringWriter()))
        return response
    }

    @Test
    fun `같은 Worker 이그레스를 거쳐도 클라이언트 IP 가 다르면 버킷을 따로 쓴다`() {
        val first = interceptor.preHandle(workerRequest(secret, "203.0.113.1"), response(), Any())
        val second = interceptor.preHandle(workerRequest(secret, "203.0.113.2"), response(), Any())

        assertThat(first).isTrue()
        assertThat(second).isTrue()
    }

    @Test
    fun `같은 클라이언트 IP 는 같은 버킷을 쓰므로 두 번째 요청이 막힌다`() {
        interceptor.preHandle(workerRequest(secret, "203.0.113.1"), response(), Any())
        val second = interceptor.preHandle(workerRequest(secret, "203.0.113.1"), response(), Any())

        assertThat(second).isFalse()
    }

    @Test
    fun `비밀이 틀린 요청은 클라이언트 IP 헤더를 위조해도 버킷을 갈아탈 수 없다`() {
        val first = interceptor.preHandle(workerRequest("wrong-secret", "203.0.113.1"), response(), Any())
        val second = interceptor.preHandle(workerRequest("wrong-secret", "203.0.113.2"), response(), Any())

        // 둘 다 Fly-Client-IP(=Worker 이그레스) 버킷으로 계산되므로 두 번째는 막힌다.
        assertThat(first).isTrue()
        assertThat(second).isFalse()
    }

    @Test
    fun `비밀이 틀린 요청은 정상 Worker 경유 사용자의 버킷을 소모하지 않는다`() {
        interceptor.preHandle(workerRequest("wrong-secret", "203.0.113.1"), response(), Any())
        val trusted = interceptor.preHandle(workerRequest(secret, "203.0.113.1"), response(), Any())

        assertThat(trusted).isTrue()
    }
}
