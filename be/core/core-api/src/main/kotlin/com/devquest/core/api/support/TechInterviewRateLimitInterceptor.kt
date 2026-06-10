package com.devquest.core.api.support

import com.devquest.core.support.error.ErrorCode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class TechInterviewRateLimitInterceptor(
    private val rateLimitBucketStore: RateLimitBucketStore,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val ip = resolveClientIp(request)
        val bucket = rateLimitBucketStore.getOrCreate(ip)

        return if (bucket.tryConsume(1)) {
            true
        } else {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            response.writer.apply {
                write(RATE_LIMIT_RESPONSE_JSON)
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

    companion object {
        private val RATE_LIMIT_RESPONSE_JSON: String = ObjectMapper().writeValueAsString(
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
}

@Component
class RateLimitBucketStore {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun getOrCreate(ip: String): Bucket = buckets.computeIfAbsent(ip) { newBucket() }

    fun clear() = buckets.clear()

    private fun newBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(2)
                .refillIntervally(2, Duration.ofDays(1))
                .build()
        )
        .build()
}
