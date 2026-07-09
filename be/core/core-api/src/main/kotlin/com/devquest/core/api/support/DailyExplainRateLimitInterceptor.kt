package com.devquest.core.api.support

import com.devquest.core.support.error.ErrorCode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 데일리 질문 후속 설명(explain) 전용 레이트 리밋 인터셉터.
 * TechInterviewRateLimitInterceptor와 버킷을 공유하지 않는다 — 평가 예산과 분리된 별도 IP당 일일 예산.
 */
@Component
class DailyExplainRateLimitInterceptor(
    private val dailyExplainRateLimitBucketStore: DailyExplainRateLimitBucketStore,
) : HandlerInterceptor {

    private val rateLimitResponseJson: String by lazy {
        ObjectMapper().writeValueAsString(
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

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val ip = resolveClientIp(request)
        val bucket = dailyExplainRateLimitBucketStore.getOrCreate(ip)

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

@Component
class DailyExplainRateLimitBucketStore(
    @Value("\${devquest.rate-limit.daily-explain.capacity:5}") private val capacity: Long,
    @Value("\${devquest.rate-limit.daily-explain.refill-days:1}") private val refillDays: Long,
) {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun getOrCreate(ip: String): Bucket = buckets.computeIfAbsent(ip) { newBucket() }

    fun clear() = buckets.clear()

    private fun newBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofDays(refillDays))
                .build()
        )
        .build()
}
