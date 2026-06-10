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
class TechInterviewRateLimitInterceptor : HandlerInterceptor {

    private val objectMapper = ObjectMapper()
    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val ip = request.getHeader("Fly-Client-IP") ?: request.remoteAddr
        val bucket = buckets.getOrPut(ip) { newBucket() }

        return if (bucket.tryConsume(1)) {
            true
        } else {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            response.writer.write(
                objectMapper.writeValueAsString(
                    mapOf(
                        "result" to "FAIL",
                        "data" to null,
                        "error" to mapOf(
                            "code" to ErrorCode.RATE_LIMIT_EXCEEDED.name,
                            "message" to ErrorCode.RATE_LIMIT_EXCEEDED.message,
                        )
                    )
                )
            )
            false
        }
    }

    private fun newBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(1)
                .refillIntervally(1, Duration.ofDays(1))
                .build()
        )
        .build()
}
