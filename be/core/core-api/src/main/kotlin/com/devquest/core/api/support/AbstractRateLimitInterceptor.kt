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
 * 레이트 리밋 인터셉터 공통 베이스.
 * 429 응답 생성과 클라이언트 IP 판별 로직을 공유하고, 어떤 키로 어떤 버킷을 쓸지만 하위 클래스에 위임한다.
 *
 * - 한도 키: 기본은 클라이언트 IP([resolveKey]). 로그인 필수 경로는 인증된 사용자 ID 로 재정의한다.
 * - 클라이언트 IP 판정은 [ClientIpResolver] 가 맡는다(Worker 신뢰 헤더 → 기존 Fly-Client-IP/XFF/remoteAddr).
 *   IP 를 키로 쓰지 않는 하위 클래스(TechInterview)는 넘기지 않아도 된다 — 기본값은 비밀 미설정 = 기존 판정.
 * - 전역 상한([globalStore])을 주면 **개인 버킷 → 전역 버킷** 순서로 차감한다. 전역에서 막히면 이미 차감한
 *   개인 토큰을 되돌린다 — 자기 한도를 넘긴 한 명이 전역을 소진하지 못하게 하고,
 *   전역 때문에 막힌 사람이 개인 토큰까지 잃지 않게 하려는 순서다.
 */
abstract class AbstractRateLimitInterceptor(
    private val objectMapper: ObjectMapper,
    private val globalStore: AbstractGlobalRateLimitStore? = null,
    private val clientIpResolver: ClientIpResolver = ClientIpResolver(""),
) : HandlerInterceptor {

    private val rateLimitResponseJson: String by lazy {
        errorJson(ErrorCode.RATE_LIMIT_EXCEEDED)
    }

    private val authenticationRequiredJson: String by lazy {
        errorJson(ErrorCode.AUTHENTICATION_REQUIRED)
    }

    protected abstract fun resolveBucket(key: String): Bucket

    /** 한도 키. null 이면 키를 정할 수 없는 요청이므로 차감 없이 403 으로 거부한다. */
    protected open fun resolveKey(request: HttpServletRequest): String? = clientIpResolver.resolve(request)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val key = resolveKey(request)
            ?: return reject(response, HttpStatus.FORBIDDEN, authenticationRequiredJson)
        val bucket = resolveBucket(key)

        if (!bucket.tryConsume(1)) {
            return reject(response, HttpStatus.TOO_MANY_REQUESTS, rateLimitResponseJson)
        }
        if (globalStore != null && !globalStore.bucket().tryConsume(1)) {
            bucket.addTokens(1)
            return reject(response, HttpStatus.TOO_MANY_REQUESTS, rateLimitResponseJson)
        }
        return true
    }

    private fun reject(response: HttpServletResponse, status: HttpStatus, body: String): Boolean {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.apply {
            write(body)
            flush()
        }
        return false
    }

    private fun errorJson(code: ErrorCode): String = objectMapper.writeValueAsString(
        mapOf(
            "result" to "ERROR",
            "data" to null,
            "error" to mapOf(
                "code" to code.name,
                "message" to code.message,
            )
        )
    )
}
