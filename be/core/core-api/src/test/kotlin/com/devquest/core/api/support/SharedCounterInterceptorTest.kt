package com.devquest.core.api.support

import com.devquest.core.domain.model.RateLimitScopes
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 인터셉터가 **공유 DB 카운터**를 실제로 쓰는지, DB 가 실패하면 메모리 버킷으로 대체하는지 (U-6).
 * 기존 인터셉터 테스트는 대체(메모리) 경로로 도는 [memoryOnlyLimiter] 를 쓰므로, DB 경로는 여기서 고정한다.
 */
class SharedCounterInterceptorTest {

    private class Fixture(
        personalCapacity: Long = 5,
        globalCapacity: Long = 100,
        val port: FakeRateLimitCounterPort = FakeRateLimitCounterPort(),
    ) {
        val personalStore = DailyExplainRateLimitBucketStore(capacity = personalCapacity, refillDays = 1)
        val globalStore = DailyExplainGlobalRateLimitStore(capacity = globalCapacity, refillDays = 1)
        val interceptor = DailyExplainRateLimitInterceptor(
            personalStore, globalStore, ObjectMapper(), SharedRateLimiter(port), ClientIpResolver(""),
        )

        fun call(ip: String): Pair<Boolean, HttpServletResponse> {
            val response = mockResponse()
            return interceptor.preHandle(mockRequest(ip), response, Any()) to response
        }
    }

    @Test
    fun `DB 경로 - 개인과 전체 카운터를 각 scope 로 차감하고 메모리 버킷은 만들지 않는다`() {
        val f = Fixture()

        assertThat(f.call("1.1.1.1").first).isTrue()
        assertThat(f.call("1.1.1.1").first).isTrue()

        assertThat(f.port.usedOf(RateLimitScopes.DAILY_EXPLAIN, "1.1.1.1")).isEqualTo(2)
        assertThat(f.port.usedOf(RateLimitScopes.DAILY_EXPLAIN_GLOBAL, RateLimitScopes.GLOBAL_KEY)).isEqualTo(2)
        assertThat(f.personalStore.size()).isEqualTo(0)
        assertThat(f.globalStore.bucket().availableTokens).isEqualTo(100)
    }

    @Test
    fun `DB 경로 - 개인 한도를 넘으면 429 이고 전체 카운터는 늘지 않는다`() {
        val f = Fixture(personalCapacity = 1)
        assertThat(f.call("1.1.1.1").first).isTrue()

        val (allowed, response) = f.call("1.1.1.1")

        assertThat(allowed).isFalse()
        verify(response).setStatus(429)
        assertThat(f.port.usedOf(RateLimitScopes.DAILY_EXPLAIN_GLOBAL, RateLimitScopes.GLOBAL_KEY)).isEqualTo(1)
    }

    @Test
    fun `DB 경로 - 전체에서 막히면 이미 차감한 개인 카운터를 DB 에서 되돌린다`() {
        val f = Fixture(personalCapacity = 2, globalCapacity = 1)
        assertThat(f.call("1.1.1.1").first).isTrue()

        assertThat(f.call("2.2.2.2").first).isFalse()
        assertThat(f.call("2.2.2.2").first).isFalse()

        assertThat(f.port.usedOf(RateLimitScopes.DAILY_EXPLAIN, "2.2.2.2")).isEqualTo(0)
        assertThat(f.port.refundCalls).containsExactly(
            RateLimitScopes.DAILY_EXPLAIN to "2.2.2.2",
            RateLimitScopes.DAILY_EXPLAIN to "2.2.2.2",
        )
    }

    @Test
    fun `DB 경로 - 한 인스턴스가 쓴 카운터를 다른 인스턴스(같은 DB)가 이어서 센다`() {
        val sharedPort = FakeRateLimitCounterPort()
        val fly = Fixture(personalCapacity = 1, port = sharedPort)
        val eks = Fixture(personalCapacity = 1, port = sharedPort)

        assertThat(fly.call("1.1.1.1").first).isTrue()

        // 전환 뒤 다른 인스턴스 — 메모리 버킷이었다면 여기서 또 통과해 한도가 2배가 된다
        assertThat(eks.call("1.1.1.1").first).isFalse()
    }

    @Test
    fun `DB 실패 - 같은 정책의 메모리 버킷으로 대체되고 429 계약은 그대로다`() {
        val f = Fixture(personalCapacity = 1, port = FakeRateLimitCounterPort(failing = true))

        assertThat(f.call("1.1.1.1").first).isTrue()
        val (allowed, response) = f.call("1.1.1.1")

        assertThat(allowed).isFalse()
        verify(response).setStatus(429)
        assertThat(f.personalStore.getOrCreate("1.1.1.1").availableTokens).isEqualTo(0)
        assertThat(f.globalStore.bucket().availableTokens).isEqualTo(99)
    }

    @Test
    fun `DB 실패 - 전체 상한도 메모리로 대체되고 전체에서 막히면 메모리 개인 토큰을 되돌린다`() {
        val f = Fixture(personalCapacity = 2, globalCapacity = 1, port = FakeRateLimitCounterPort(failing = true))
        assertThat(f.call("1.1.1.1").first).isTrue()

        assertThat(f.call("2.2.2.2").first).isFalse()

        assertThat(f.personalStore.getOrCreate("2.2.2.2").availableTokens).isEqualTo(2)
    }

    @Test
    fun `DB 가 중간에 죽어도 요청은 500 이 아니라 대체 경로로 처리된다`() {
        val port = FakeRateLimitCounterPort()
        val f = Fixture(personalCapacity = 3, port = port)
        assertThat(f.call("1.1.1.1").first).isTrue()

        port.failing = true

        assertThat(f.call("1.1.1.1").first).isTrue()
    }

    @Test
    fun `scope 가 달라서 익명 explain 과 evaluate 의 같은 IP 카운터가 섞이지 않는다`() {
        val port = FakeRateLimitCounterPort()
        val limiter = SharedRateLimiter(port)
        val explain = DailyExplainRateLimitInterceptor(
            DailyExplainRateLimitBucketStore(5, 1), DailyExplainGlobalRateLimitStore(100, 1), ObjectMapper(), limiter, ClientIpResolver(""),
        )
        val evaluate = DailyEvaluateRateLimitInterceptor(
            DailyEvaluateRateLimitBucketStore(1, 1), DailyEvaluateGlobalRateLimitStore(50, 1), ObjectMapper(), limiter, ClientIpResolver(""),
        )

        assertThat(explain.preHandle(mockRequest("1.1.1.1"), mockResponse(), Any())).isTrue()
        assertThat(evaluate.preHandle(mockRequest("1.1.1.1"), mockResponse(), Any())).isTrue()

        assertThat(port.usedOf(RateLimitScopes.DAILY_EXPLAIN, "1.1.1.1")).isEqualTo(1)
        assertThat(port.usedOf(RateLimitScopes.DAILY_EVALUATE, "1.1.1.1")).isEqualTo(1)
    }

    @Test
    fun `클라이언트가 보낸 IP 문자열이 global 이어도 전체 상한 카운터와 섞이지 않는다`() {
        val f = Fixture()

        assertThat(f.call(RateLimitScopes.GLOBAL_KEY).first).isTrue()

        assertThat(f.port.usedOf(RateLimitScopes.DAILY_EXPLAIN, RateLimitScopes.GLOBAL_KEY)).isEqualTo(1)
        assertThat(f.port.usedOf(RateLimitScopes.DAILY_EXPLAIN_GLOBAL, RateLimitScopes.GLOBAL_KEY)).isEqualTo(1)
    }

    @Test
    fun `기술 면접은 로그인 사용자 ID 를 tech-interview scope 로 DB 에서 센다 - 익명이면 403 이고 차감 없음`() {
        val port = FakeRateLimitCounterPort()
        val interceptor = TechInterviewRateLimitInterceptor(
            RateLimitBucketStore(capacity = 2, refillDays = 1), ObjectMapper(), SharedRateLimiter(port),
        )
        try {
            org.springframework.security.core.context.SecurityContextHolder.getContext().authentication =
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "github-42", null, listOf(org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                )
            assertThat(interceptor.preHandle(mockRequest("9.9.9.9"), mockResponse(), Any())).isTrue()
            assertThat(interceptor.preHandle(mockRequest("8.8.8.8"), mockResponse(), Any())).isTrue()
            assertThat(interceptor.preHandle(mockRequest("7.7.7.7"), mockResponse(), Any())).isFalse()
            assertThat(port.usedOf(RateLimitScopes.TECH_INTERVIEW, "github-42")).isEqualTo(2)

            org.springframework.security.core.context.SecurityContextHolder.clearContext()
            val response = mockResponse()
            assertThat(interceptor.preHandle(mockRequest("9.9.9.9"), response, Any())).isFalse()
            verify(response).setStatus(403)
            assertThat(port.consumeCalls).hasSize(3)
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext()
        }
    }

    companion object {
        private fun mockRequest(ip: String): HttpServletRequest {
            val request = mock<HttpServletRequest>()
            whenever(request.getHeader("Fly-Client-IP")).thenReturn(null)
            whenever(request.getHeader("X-Forwarded-For")).thenReturn(null)
            whenever(request.remoteAddr).thenReturn(ip)
            return request
        }

        private fun mockResponse(): HttpServletResponse {
            val response = mock<HttpServletResponse>()
            whenever(response.writer).thenReturn(PrintWriter(StringWriter()))
            return response
        }
    }
}
