package com.devquest.core.api.support

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * 익명 엔드포인트 하나의 **전체(전역) 일일 상한** 버킷 — 키 없이 단일 버킷이다.
 *
 * IP 개인 버킷은 IP를 바꾸면 우회되므로, 익명 경로에서 AI 비용이 무한정 늘지 않게 하는 마지막 방벽이다.
 * 자정 리셋은 버킷을 새로 갈아 끼운다([clear]) — `AbstractRateLimitBucketStore.clear()` 와 같은 의미.
 */
abstract class AbstractGlobalRateLimitStore(
    private val capacity: Long,
    private val refillDays: Long,
) {

    private val bucket = AtomicReference(newBucket())

    fun bucket(): Bucket = bucket.get()

    fun clear() = bucket.set(newBucket())

    private fun newBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofDays(refillDays))
                .build()
        )
        .build()
}
