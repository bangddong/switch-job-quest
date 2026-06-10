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

class TechInterviewRateLimitInterceptorTest {

    private lateinit var store: RateLimitBucketStore
    private lateinit var interceptor: TechInterviewRateLimitInterceptor

    @BeforeEach
    fun setUp() {
        store = RateLimitBucketStore()
        interceptor = TechInterviewRateLimitInterceptor(store)
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
    fun `첫 번째 요청은 통과한다`() {
        val result = interceptor.preHandle(mockRequest("1.2.3.4"), mockResponse(), Any())
        assertThat(result).isTrue()
    }

    @Test
    fun `같은 IP에서 두 번째 요청(평가)도 통과한다`() {
        val request = mockRequest("1.2.3.4")
        interceptor.preHandle(request, mockResponse(), Any())
        val result = interceptor.preHandle(request, mockResponse(), Any())
        assertThat(result).isTrue()
    }

    @Test
    fun `같은 IP에서 세 번째 요청은 차단된다`() {
        val request = mockRequest("1.2.3.4")
        interceptor.preHandle(request, mockResponse(), Any())
        interceptor.preHandle(request, mockResponse(), Any())
        val result = interceptor.preHandle(request, mockResponse(), Any())
        assertThat(result).isFalse()
    }

    @Test
    fun `다른 IP는 독립적으로 카운트된다`() {
        interceptor.preHandle(mockRequest("1.2.3.4"), mockResponse(), Any())
        val result = interceptor.preHandle(mockRequest("5.6.7.8"), mockResponse(), Any())
        assertThat(result).isTrue()
    }

    @Test
    fun `Fly-Client-IP 헤더가 있으면 해당 IP로 식별된다`() {
        // remoteAddr이 다르더라도 Fly-Client-IP 기준으로 동일 IP 취급
        interceptor.preHandle(mockRequest("10.0.0.1", flyClientIp = "1.2.3.4"), mockResponse(), Any())
        interceptor.preHandle(mockRequest("10.0.0.2", flyClientIp = "1.2.3.4"), mockResponse(), Any())
        val result = interceptor.preHandle(mockRequest("10.0.0.3", flyClientIp = "1.2.3.4"), mockResponse(), Any())
        assertThat(result).isFalse()
    }

    @Test
    fun `store clear 후에는 다시 2회 허용된다`() {
        val request = mockRequest("1.2.3.4")
        interceptor.preHandle(request, mockResponse(), Any())
        interceptor.preHandle(request, mockResponse(), Any())
        store.clear()
        val result = interceptor.preHandle(request, mockResponse(), Any())
        assertThat(result).isTrue()
    }
}
