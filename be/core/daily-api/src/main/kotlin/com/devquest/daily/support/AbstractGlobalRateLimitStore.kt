package com.devquest.daily.support

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * 익명 엔드포인트 하나의 **전체(전역) 일일 상한**의 메모리 대체 버킷 — 키 없이 단일 버킷이다.
 *
 * IP 개인 한도는 IP를 바꾸면 우회되므로, 익명 경로에서 AI 비용이 무한정 늘지 않게 하는 마지막 방벽이다.
 * 본 저장소는 공유 DB 카운터(scope=[scope], 고정 키)이고, 이 버킷은 DB 를 쓸 수 없을 때만 쓰인다.
 * 자정 리셋은 버킷을 새로 갈아 끼운다([clear]) — `AbstractRateLimitBucketStore.clear()` 와 같은 의미.
 */
abstract class AbstractGlobalRateLimitStore(
    val scope: String,
    val capacity: Long,
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
