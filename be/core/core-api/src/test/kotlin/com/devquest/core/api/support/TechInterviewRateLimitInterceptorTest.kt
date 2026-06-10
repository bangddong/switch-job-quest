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

    private lateinit var interceptor: TechInterviewRateLimitInterceptor

    @BeforeEach
    fun setUp() {
        interceptor = TechInterviewRateLimitInterceptor()
    }

    private fun mockRequest(ip: String): HttpServletRequest {
        val request = mock<HttpServletRequest>()
        whenever(request.getHeader("Fly-Client-IP")).thenReturn(null)
        whenever(request.remoteAddr).thenReturn(ip)
        return request
    }

    private fun mockResponse(): HttpServletResponse {
        val response = mock<HttpServletResponse>()
        val writer = StringWriter()
        whenever(response.writer).thenReturn(PrintWriter(writer))
        return response
    }

    @Test
    fun `첫 번째 요청은 통과한다`() {
        val result = interceptor.preHandle(mockRequest("1.2.3.4"), mockResponse(), Any())
        assertThat(result).isTrue()
    }

    @Test
    fun `같은 IP에서 두 번째 요청은 차단된다`() {
        val request = mockRequest("1.2.3.4")
        interceptor.preHandle(request, mockResponse(), Any())
        val result = interceptor.preHandle(request, mockResponse(), Any())
        assertThat(result).isFalse()
    }

    @Test
    fun `다른 IP는 독립적으로 카운트된다`() {
        interceptor.preHandle(mockRequest("1.2.3.4"), mockResponse(), Any())  // IP-A 소진
        val result = interceptor.preHandle(mockRequest("5.6.7.8"), mockResponse(), Any())  // IP-B 첫 시도
        assertThat(result).isTrue()
    }
}
