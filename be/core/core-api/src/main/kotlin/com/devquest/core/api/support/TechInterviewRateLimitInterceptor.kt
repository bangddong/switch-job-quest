package com.devquest.core.api.support

import io.github.bucket4j.Bucket
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 기술 면접(tech-interview 하위 전 경로) 레이트 리밋. 로그인 필수 경로라 한도 키는 IP 가 아니라
 * **인증된 사용자 ID**(JWT principal, `github-<id>`)다 — IP 는 조작할 수 있다.
 *
 * Spring Security 가 이 인터셉터보다 앞에서 미인증 요청을 막으므로 정상 경로에선 항상 사용자가 있다.
 * 그래도 인증이 없거나 익명이면 차감하지 않고 거부한다(null 키 → 403).
 */
@Component
class TechInterviewRateLimitInterceptor(
    private val rateLimitBucketStore: RateLimitBucketStore,
    objectMapper: ObjectMapper,
) : AbstractRateLimitInterceptor(objectMapper) {

    override fun resolveKey(request: HttpServletRequest): String? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        // permitAll 경로의 익명 요청도 principal 이 문자열 "anonymousUser" 로 들어오므로
        // `principal as? String` 만으로는 사용자를 판정할 수 없다.
        if (!authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) return null
        val userId = authentication.principal as? String ?: return null
        return userId.takeUnless { it.isBlank() || it == ANONYMOUS_PRINCIPAL }
    }

    override fun resolveBucket(key: String): Bucket = rateLimitBucketStore.getOrCreate(key)

    private companion object {
        const val ANONYMOUS_PRINCIPAL = "anonymousUser"
    }
}

@Component
class RateLimitBucketStore(
    @Value("\${devquest.rate-limit.tech-interview.capacity:2}") capacity: Long,
    @Value("\${devquest.rate-limit.tech-interview.refill-days:1}") refillDays: Long,
) : AbstractRateLimitBucketStore(capacity, refillDays)
