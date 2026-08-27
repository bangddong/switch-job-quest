package com.devquest.daily.support

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

/** core-api `DailyExplainRateLimitInterceptorTest`와 동일 패턴(D-007 복제 컴포넌트). */
class DailyExplainRateLimitInterceptorTest {

    private lateinit var store: DailyExplainRateLimitBucketStore
    private lateinit var interceptor: DailyExplainRateLimitInterceptor

    @BeforeEach
    fun setUp() {
        store = DailyExplainRateLimitBucketStore(capacity = 5, refillDays = 1)
        interceptor = DailyExplainRateLimitInterceptor(store, ObjectMapper())
    }

    private fun mockRequest(ip: String): HttpServletRequest {
        val request = mock<HttpServletRequest>()
        whenever(request.getHeader("Fly-Client-IP")).thenReturn(null)
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
    fun `용량(5) 이내 요청은 모두 통과한다`() {
        val request = mockRequest("1.2.3.4")
        repeat(5) {
            assertThat(interceptor.preHandle(request, mockResponse(), Any())).isTrue()
        }
    }

    @Test
    fun `용량을 초과한 6번째 요청은 429로 차단된다`() {
        val request = mockRequest("1.2.3.4")
        repeat(5) { interceptor.preHandle(request, mockResponse(), Any()) }

        val response = mockResponse()
        val result = interceptor.preHandle(request, response, Any())

        assertThat(result).isFalse()
        org.mockito.kotlin.verify(response).status = 429
    }
}
