package com.devquest.core.api.support

import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 데일리 질문 후속 설명(explain) 전용 레이트 리밋 인터셉터.
 * TechInterviewRateLimitInterceptor와 버킷을 공유하지 않는다 — 평가 예산과 분리된 별도 IP당 일일 예산.
 */
@Component
class DailyExplainRateLimitInterceptor(
    private val dailyExplainRateLimitBucketStore: DailyExplainRateLimitBucketStore,
    globalStore: DailyExplainGlobalRateLimitStore,
    objectMapper: ObjectMapper,
) : AbstractRateLimitInterceptor(objectMapper, globalStore) {

    override fun resolveBucket(key: String): Bucket = dailyExplainRateLimitBucketStore.getOrCreate(key)
}

@Component
class DailyExplainRateLimitBucketStore(
    @Value("\${devquest.rate-limit.daily-explain.capacity:5}") capacity: Long,
    @Value("\${devquest.rate-limit.daily-explain.refill-days:1}") refillDays: Long,
) : AbstractRateLimitBucketStore(capacity, refillDays)

/**
 * 익명 explain 경로의 **전체 일일 상한**. IP 개인 버킷을 통과한 요청만 여기서 차감된다
 * (`AbstractRateLimitInterceptor` 의 개인 → 전역 순서).
 */
@Component
class DailyExplainGlobalRateLimitStore(
    @Value("\${devquest.rate-limit.daily-explain.global-capacity:100}") capacity: Long,
    @Value("\${devquest.rate-limit.daily-explain.refill-days:1}") refillDays: Long,
) : AbstractGlobalRateLimitStore(capacity, refillDays)
