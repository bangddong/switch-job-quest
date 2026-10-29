package com.devquest.core.api.support

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
 * 익명 daily explain/evaluate 의 **전체(전역) 일일 상한** (📌D-016 B7-19 1단계).
 *
 * 핵심은 순서다: 개인(IP) 버킷 먼저 → 통과한 경우에만 전역 차감 → 전역에서 막히면 개인 토큰 환불.
 * 자기 한도를 넘긴 한 명이 전역 예산을 소진하지 못해야 하고, 전역에 막힌 사람이 개인 토큰까지 잃지 않아야 한다.
 */
class DailyGlobalRateLimitTest {

    private class Fixture(personalCapacity: Long, globalCapacity: Long) {
        val personalStore = DailyExplainRateLimitBucketStore(capacity = personalCapacity, refillDays = 1)
        val globalStore = DailyExplainGlobalRateLimitStore(capacity = globalCapacity, refillDays = 1)
        val interceptor = DailyExplainRateLimitInterceptor(personalStore, globalStore, ObjectMapper(), ClientIpResolver(""))

        fun call(ip: String): Pair<Boolean, HttpServletResponse> {
            val response = mockResponse()
            return interceptor.preHandle(mockRequest(ip), response, Any()) to response
        }
    }

    @Test
    fun `전역 상한을 다 쓰면 다른 IP의 첫 요청도 429로 차단된다`() {
        val f = Fixture(personalCapacity = 5, globalCapacity = 2)
        assertThat(f.call("1.1.1.1").first).isTrue()
        assertThat(f.call("2.2.2.2").first).isTrue()

        val (allowed, response) = f.call("3.3.3.3")

        assertThat(allowed).isFalse()
        verify(response).setStatus(429)
    }

    @Test
    fun `통과한 요청마다 전역 버킷이 하나씩 차감된다`() {
        val f = Fixture(personalCapacity = 5, globalCapacity = 10)
        f.call("1.1.1.1")
        f.call("1.1.1.1")
        f.call("2.2.2.2")

        assertThat(f.globalStore.bucket().availableTokens).isEqualTo(7)
    }

    @Test
    fun `개인 한도에 막힌 요청은 전역 버킷을 차감하지 않는다 - 한 명이 전역을 소진하지 못한다`() {
        val f = Fixture(personalCapacity = 1, globalCapacity = 10)
        assertThat(f.call("1.1.1.1").first).isTrue()
        assertThat(f.globalStore.bucket().availableTokens).isEqualTo(9)

        repeat(20) { assertThat(f.call("1.1.1.1").first).isFalse() }

        assertThat(f.globalStore.bucket().availableTokens).isEqualTo(9)
    }

    @Test
    fun `전역에서 막히면 이미 차감한 개인 토큰을 되돌린다`() {
        val f = Fixture(personalCapacity = 2, globalCapacity = 1)
        assertThat(f.call("1.1.1.1").first).isTrue()
        // 전역이 소진된 상태에서 다른 IP 가 두 번 시도 — 개인 토큰이 환불되지 않으면 여기서 2회 모두 소진된다
        assertThat(f.call("2.2.2.2").first).isFalse()
        assertThat(f.call("2.2.2.2").first).isFalse()
        assertThat(f.personalStore.getOrCreate("2.2.2.2").availableTokens).isEqualTo(2)

        // 자정 리셋 이후 통과한 요청은 개인 토큰을 정확히 1개만 쓴다 (실패한 두 번은 비용이 0이었다)
        f.globalStore.clear()
        assertThat(f.call("2.2.2.2").first).isTrue()
        assertThat(f.personalStore.getOrCreate("2.2.2.2").availableTokens).isEqualTo(1)
    }

    @Test
    fun `전역 store clear 후에는 상한이 다시 채워진다`() {
        val f = Fixture(personalCapacity = 5, globalCapacity = 1)
        f.call("1.1.1.1")
        assertThat(f.call("2.2.2.2").first).isFalse()

        f.globalStore.clear()

        assertThat(f.call("2.2.2.2").first).isTrue()
    }

    @Test
    fun `daily-evaluate 도 같은 순서로 동작한다`() {
        val personal = DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1)
        val global = DailyEvaluateGlobalRateLimitStore(capacity = 1, refillDays = 1)
        val interceptor = DailyEvaluateRateLimitInterceptor(personal, global, ObjectMapper(), ClientIpResolver(""))

        assertThat(interceptor.preHandle(mockRequest("1.1.1.1"), mockResponse(), Any())).isTrue()
        assertThat(interceptor.preHandle(mockRequest("2.2.2.2"), mockResponse(), Any())).isFalse()
        assertThat(personal.getOrCreate("2.2.2.2").availableTokens).isEqualTo(1)
        // 개인 한도에 막힌 요청은 전역을 건드리지 않는다
        global.clear()
        assertThat(interceptor.preHandle(mockRequest("1.1.1.1"), mockResponse(), Any())).isFalse()
        assertThat(global.bucket().availableTokens).isEqualTo(1)
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
