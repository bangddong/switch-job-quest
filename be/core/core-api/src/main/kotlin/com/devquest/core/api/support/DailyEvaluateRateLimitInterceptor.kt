package com.devquest.core.api.support

import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 데일리 질문 평가(evaluate) 전용 레이트 리밋 인터셉터.
 * TechInterviewRateLimitInterceptor와 버킷을 공유하지 않는다 — 무로그인 공개 경로라 보수적으로 별도 예산(1/일)을 둔다.
 */
@Component
class DailyEvaluateRateLimitInterceptor(
    private val dailyEvaluateRateLimitBucketStore: DailyEvaluateRateLimitBucketStore,
    objectMapper: ObjectMapper,
) : AbstractRateLimitInterceptor(objectMapper) {

    override fun resolveBucket(ip: String): Bucket = dailyEvaluateRateLimitBucketStore.getOrCreate(ip)
}

@Component
class DailyEvaluateRateLimitBucketStore(
    @Value("\${devquest.rate-limit.daily-evaluate.capacity:1}") capacity: Long,
    @Value("\${devquest.rate-limit.daily-evaluate.refill-days:1}") refillDays: Long,
) : AbstractRateLimitBucketStore(capacity, refillDays)
