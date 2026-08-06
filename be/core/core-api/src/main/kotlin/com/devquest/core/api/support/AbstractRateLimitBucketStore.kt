package com.devquest.core.api.support

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * IP당 일일 토큰 버킷을 관리하는 공통 베이스.
 * capacity·refillDays만 다른 여러 레이트 리밋 정책이 이 클래스를 상속해
 * 서로 독립된 버킷 저장소를 갖는다.
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
