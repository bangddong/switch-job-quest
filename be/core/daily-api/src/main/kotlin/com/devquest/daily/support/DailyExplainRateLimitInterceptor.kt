package com.devquest.daily.support

import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 데일리 질문 후속 설명(explain) 전용 레이트 리밋 인터셉터. core-api와 동일 정책(IP당 일 5회).
 */
@Component
class DailyExplainRateLimitInterceptor(
    private val dailyExplainRateLimitBucketStore: DailyExplainRateLimitBucketStore,
    objectMapper: ObjectMapper,
) : AbstractRateLimitInterceptor(objectMapper) {

    override fun resolveBucket(ip: String): Bucket = dailyExplainRateLimitBucketStore.getOrCreate(ip)
}

@Component
class DailyExplainRateLimitBucketStore(
    @Value("\${devquest.rate-limit.daily-explain.capacity:5}") capacity: Long,
    @Value("\${devquest.rate-limit.daily-explain.refill-days:1}") refillDays: Long,
) : AbstractRateLimitBucketStore(capacity, refillDays)
