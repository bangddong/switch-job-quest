package com.devquest.daily.support

import com.devquest.daily.controller.response.DailyErrorCode
import io.github.bucket4j.Bucket
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.servlet.HandlerInterceptor
import tools.jackson.databind.ObjectMapper

/**
 * core-api `AbstractRateLimitInterceptor`와 동일한 IP 기반 레이트 리밋 공통 베이스
 * (D-007 — core-api를 의존할 수 없어 daily-api 자체 소유로 복제).
 */
abstract class AbstractRateLimitInterceptor(
    private val objectMapper: ObjectMapper,
) : HandlerInterceptor {

    private val rateLimitResponseJson: String by lazy {
        objectMapper.writeValueAsString(
            mapOf(
                "result" to "ERROR",
                "data" to null,
                "error" to mapOf(
                    "code" to DailyErrorCode.RATE_LIMIT_EXCEEDED.name,
                    "message" to DailyErrorCode.RATE_LIMIT_EXCEEDED.message,
                )
            )
        )
    }

    protected abstract fun resolveBucket(ip: String): Bucket

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val ip = resolveClientIp(request)
        val bucket = resolveBucket(ip)

        return if (bucket.tryConsume(1)) {
            true
        } else {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            response.writer.apply {
                write(rateLimitResponseJson)
                flush()
            }
            false
        }
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        // Fly.io 전용 헤더 → X-Forwarded-For 첫 번째 IP → remoteAddr 순으로 폴백 (core-api와 동일).
        return request.getHeader("Fly-Client-IP")
            ?: request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
    }
}
