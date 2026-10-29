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
 *
 * 전역 상한([globalStore])은 **개인(IP) 버킷 → 전역 버킷** 순서로 차감한다. 전역에서 막히면 이미 차감한
 * 개인 토큰을 되돌린다 — 자기 한도를 넘긴 한 명이 전역을 소진하지 못하게 하고, 전역 때문에 막힌 사람이
 * 개인 토큰까지 잃지 않게 하려는 순서다. daily-api 는 익명 경로뿐이라 로그인 키(403) 분기는 없다.
 *
 * 클라이언트 IP 판정은 [ClientIpResolver] 가 맡는다(Worker 신뢰 헤더 → 기존 Fly-Client-IP/XFF/remoteAddr).
 */
abstract class AbstractRateLimitInterceptor(
    private val objectMapper: ObjectMapper,
    private val clientIpResolver: ClientIpResolver,
    private val globalStore: AbstractGlobalRateLimitStore? = null,
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

    protected abstract fun resolveBucket(key: String): Bucket

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val bucket = resolveBucket(clientIpResolver.resolve(request))

        if (!bucket.tryConsume(1)) {
            return reject(response)
        }
        if (globalStore != null && !globalStore.bucket().tryConsume(1)) {
            bucket.addTokens(1)
            return reject(response)
        }
        return true
    }

    private fun reject(response: HttpServletResponse): Boolean {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.apply {
            write(rateLimitResponseJson)
            flush()
        }
        return false
    }
}
