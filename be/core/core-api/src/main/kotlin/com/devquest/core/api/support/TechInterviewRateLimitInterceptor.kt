package com.devquest.core.api.support

import com.devquest.core.support.error.ErrorCode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class TechInterviewRateLimitInterceptor : HandlerInterceptor {

    private val objectMapper = ObjectMapper()
    private val buckets = ConcurrentHashMap<String, Bucket>()

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun clearBuckets() {
        buckets.clear()
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val ip = request.getHeader("Fly-Client-IP") ?: request.remoteAddr
        val bucket = buckets.computeIfAbsent(ip) { newBucket() }

        return if (bucket.tryConsume(1)) {
            true
        } else {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            response.writer.apply {
                write(
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
                )
                flush()
            }
            false
        }
    }

    private fun newBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(2)
                .refillIntervally(2, Duration.ofDays(1))
                .build()
        )
        .build()
}
