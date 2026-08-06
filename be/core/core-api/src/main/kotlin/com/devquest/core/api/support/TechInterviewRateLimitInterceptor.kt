package com.devquest.core.api.support

import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class TechInterviewRateLimitInterceptor(
    private val rateLimitBucketStore: RateLimitBucketStore,
    objectMapper: ObjectMapper,
) : AbstractRateLimitInterceptor(objectMapper) {

    override fun resolveBucket(ip: String): Bucket = rateLimitBucketStore.getOrCreate(ip)
}

@Component
class RateLimitBucketStore(
    @Value("\${devquest.rate-limit.tech-interview.capacity:2}") capacity: Long,
    @Value("\${devquest.rate-limit.tech-interview.refill-days:1}") refillDays: Long,
) : AbstractRateLimitBucketStore(capacity, refillDays)
