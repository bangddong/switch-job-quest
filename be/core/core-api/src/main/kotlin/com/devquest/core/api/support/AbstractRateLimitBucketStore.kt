package com.devquest.core.api.support

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 키(IP 또는 인증된 사용자 ID)당 일일 토큰 버킷을 관리하는 공통 베이스.
 * capacity·refillDays만 다른 여러 레이트 리밋 정책이 이 클래스를 상속해
 * 서로 독립된 버킷 저장소를 갖는다.
 */
abstract class AbstractRateLimitBucketStore(
    private val capacity: Long,
    private val refillDays: Long,
) {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun getOrCreate(key: String): Bucket = buckets.computeIfAbsent(key) { newBucket() }

    fun clear() = buckets.clear()

    /** 현재 보유한 버킷 수 (키 하나당 버킷 하나) — 거부된 요청이 버킷을 만들지 않았는지 검증하는 데 쓴다. */
    fun size(): Int = buckets.size

    private fun newBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofDays(refillDays))
                .build()
        )
        .build()
}
