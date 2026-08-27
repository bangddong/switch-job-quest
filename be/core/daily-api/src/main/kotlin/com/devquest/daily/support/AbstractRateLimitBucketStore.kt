package com.devquest.daily.support

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * core-api `AbstractRateLimitBucketStore`와 동일한 IP당 일일 토큰 버킷 공통 베이스
 * (D-007 — core-api를 의존할 수 없어 daily-api 자체 소유로 복제).
 */
abstract class AbstractRateLimitBucketStore(
    private val capacity: Long,
    private val refillDays: Long,
) {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun getOrCreate(ip: String): Bucket = buckets.computeIfAbsent(ip) { newBucket() }

    fun clear() = buckets.clear()

    private fun newBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofDays(refillDays))
                .build()
        )
        .build()
}
