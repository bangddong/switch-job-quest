package com.devquest.core.api.support

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.PrintWriter
import java.io.StringWriter

class DailyExplainRateLimitInterceptorTest {

    private lateinit var store: DailyExplainRateLimitBucketStore
    private lateinit var interceptor: DailyExplainRateLimitInterceptor

    @BeforeEach
    fun setUp() {
        store = DailyExplainRateLimitBucketStore(capacity = 5, refillDays = 1)
        interceptor = DailyExplainRateLimitInterceptor(store)
    }

    private fun mockRequest(ip: String, flyClientIp: String? = null): HttpServletRequest {
        val request = mock<HttpServletRequest>()
        whenever(request.getHeader("Fly-Client-IP")).thenReturn(flyClientIp)
        whenever(request.getHeader("X-Forwarded-For")).thenReturn(null)
        whenever(request.remoteAddr).thenReturn(ip)
        return request
    }

    private fun mockResponse(): HttpServletResponse {
        val response = mock<HttpServletResponse>()
        val writer = PrintWriter(StringWriter())
        whenever(response.writer).thenReturn(writer)
        return response
    }

    @Test
    fun `5회까지는 통과한다`() {
        val request = mockRequest("1.2.3.4")
        repeat(5) {
            val result = interceptor.preHandle(request, mockResponse(), Any())
            assertThat(result).isTrue()
        }
    }

    @Test
    fun `6번째 요청은 차단된다`() {
        val request = mockRequest("1.2.3.4")
        repeat(5) { interceptor.preHandle(request, mockResponse(), Any()) }
        val result = interceptor.preHandle(request, mockResponse(), Any())
        assertThat(result).isFalse()
    }

    @Test
    fun `다른 IP는 독립적으로 카운트된다`() {
        val request = mockRequest("1.2.3.4")
        repeat(5) { interceptor.preHandle(request, mockResponse(), Any()) }
        val result = interceptor.preHandle(mockRequest("5.6.7.8"), mockResponse(), Any())
        assertThat(result).isTrue()
    }

    @Test
    fun `store clear 후에는 다시 5회 허용된다`() {
        val request = mockRequest("1.2.3.4")
        repeat(5) { interceptor.preHandle(request, mockResponse(), Any()) }
        store.clear()
        val result = interceptor.preHandle(request, mockResponse(), Any())
        assertThat(result).isTrue()
    }
}
