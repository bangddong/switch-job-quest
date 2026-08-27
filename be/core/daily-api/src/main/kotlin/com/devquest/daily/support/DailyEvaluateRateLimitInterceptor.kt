package com.devquest.daily.support

import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 데일리 질문 평가(evaluate) 전용 레이트 리밋 인터셉터. core-api와 동일 정책(IP당 일 1회, 무로그인
 * 공개 경로라 보수적으로 별도 예산).
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
