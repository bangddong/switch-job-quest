package com.devquest.core.api.support

import com.devquest.core.support.error.ErrorCode
import io.github.bucket4j.Bucket
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.servlet.HandlerInterceptor
import tools.jackson.databind.ObjectMapper

/**
 * IP 기반 레이트 리밋 인터셉터 공통 베이스.
 * 429 응답 생성과 클라이언트 IP 판별 로직을 공유하고, 어떤 버킷을 쓸지만 하위 클래스에 위임한다.
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
                    "code" to ErrorCode.RATE_LIMIT_EXCEEDED.name,
                    "message" to ErrorCode.RATE_LIMIT_EXCEEDED.message,
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
        // Fly.io 전용 헤더 → X-Forwarded-For 첫 번째 IP → remoteAddr 순으로 폴백
        return request.getHeader("Fly-Client-IP")
            ?: request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
    }
}
