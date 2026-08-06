package com.devquest.core.api.support

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.io.PrintWriter
import java.io.StringWriter

/**
 * daily-question evaluate를 tech-interview 버킷에서 분리한 목적을 검증한다:
 * 같은 IP라도 한쪽 버킷이 소진돼도 다른 쪽은 영향을 받지 않아야 한다.
 */
class RateLimitBucketIsolationTest {

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
    fun `tech-interview 버킷을 소진해도 같은 IP의 daily-evaluate 버킷은 영향받지 않는다`() {
        val techInterviewInterceptor = TechInterviewRateLimitInterceptor(
            RateLimitBucketStore(capacity = 2, refillDays = 1),
            ObjectMapper(),
        )
        val dailyEvaluateInterceptor = DailyEvaluateRateLimitInterceptor(
            DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1),
            ObjectMapper(),
        )
        val ip = "1.2.3.4"

        // tech-interview 버킷(용량 2)을 소진시켜 3번째 요청은 차단된다
        techInterviewInterceptor.preHandle(mockRequest(ip), mockResponse(), Any())
        techInterviewInterceptor.preHandle(mockRequest(ip), mockResponse(), Any())
        val techInterviewBlocked = techInterviewInterceptor.preHandle(mockRequest(ip), mockResponse(), Any())
        assertThat(techInterviewBlocked).isFalse()

        // 같은 IP라도 daily-evaluate 버킷(용량 1)은 소진되지 않았으므로 첫 요청은 통과한다
        val dailyEvaluateAllowed = dailyEvaluateInterceptor.preHandle(mockRequest(ip), mockResponse(), Any())
        assertThat(dailyEvaluateAllowed).isTrue()
    }

    @Test
    fun `daily-evaluate 버킷을 소진해도 같은 IP의 tech-interview 버킷은 영향받지 않는다`() {
        val techInterviewInterceptor = TechInterviewRateLimitInterceptor(
            RateLimitBucketStore(capacity = 2, refillDays = 1),
            ObjectMapper(),
        )
        val dailyEvaluateInterceptor = DailyEvaluateRateLimitInterceptor(
            DailyEvaluateRateLimitBucketStore(capacity = 1, refillDays = 1),
            ObjectMapper(),
        )
        val ip = "1.2.3.4"

        // daily-evaluate 버킷(용량 1)을 소진시켜 2번째 요청은 차단된다
        dailyEvaluateInterceptor.preHandle(mockRequest(ip), mockResponse(), Any())
        val dailyEvaluateBlocked = dailyEvaluateInterceptor.preHandle(mockRequest(ip), mockResponse(), Any())
        assertThat(dailyEvaluateBlocked).isFalse()

        // 같은 IP라도 tech-interview 버킷(용량 2)은 소진되지 않았으므로 여전히 통과한다
        val techInterviewAllowed = techInterviewInterceptor.preHandle(mockRequest(ip), mockResponse(), Any())
        assertThat(techInterviewAllowed).isTrue()
    }
}
