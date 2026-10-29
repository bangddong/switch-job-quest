package com.devquest.daily.support

import com.devquest.core.domain.model.RateLimitScopes
import com.devquest.daily.controller.response.DailyErrorCode
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
 * 전역 상한([globalStore])은 **개인(IP) 한도 → 전역 상한** 순서로 차감한다. 전역에서 막히면 이미 차감한
 * 개인 토큰을 되돌린다 — 자기 한도를 넘긴 한 명이 전역을 소진하지 못하게 하고, 전역 때문에 막힌 사람이
 * 개인 토큰까지 잃지 않게 하려는 순서다. daily-api 는 익명 경로뿐이라 로그인 키(403) 분기는 없다.
 *
 * 클라이언트 IP 판정은 [ClientIpResolver] 가 맡는다(Worker 신뢰 헤더 → 기존 Fly-Client-IP/XFF/remoteAddr).
 *
 * 카운터는 공유 DB(U-6)에서 세고, DB 를 쓸 수 없으면 [SharedRateLimiter] 가 [personalStore]·[globalStore] 의
 * 메모리 버킷으로 대체한다. 개인·전역 각각 따로 대체되며, 환불은 차감한 저장소로 돌아간다.
 */
abstract class AbstractRateLimitInterceptor(
    private val objectMapper: ObjectMapper,
    private val limiter: SharedRateLimiter,
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

    /** 개인 한도의 scope·상한·메모리 대체 버킷을 가진 저장소. */
    protected abstract val personalStore: AbstractRateLimitBucketStore

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val key = clientIpResolver.resolve(request)
        val store = personalStore

        val personalTicket = limiter.tryConsume(store.scope, key, store.capacity) { store.getOrCreate(key) }
            ?: return reject(response)

        if (globalStore != null) {
            val globalTicket = limiter.tryConsume(
                globalStore.scope, RateLimitScopes.GLOBAL_KEY, globalStore.capacity,
            ) { globalStore.bucket() }
            if (globalTicket == null) {
                personalTicket.refund()
                return reject(response)
            }
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
