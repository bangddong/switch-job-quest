package com.devquest.core.api.support

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 키(IP 또는 인증된 사용자 ID)당 일일 한도의 **메모리 대체 버킷**을 관리하는 공통 베이스.
 *
 * 한도의 본 저장소는 공유 DB 카운터([SharedRateLimiter])다. 이 버킷은 DB 를 쓸 수 없을 때만 쓰인다(fail-soft).
 * [scope]·[capacity] 는 DB 카운터의 종류·상한이기도 하다 — 같은 정책이 두 저장소에서 같은 값으로 돈다.
 * capacity·refillDays만 다른 여러 레이트 리밋 정책이 이 클래스를 상속해 서로 독립된 한도를 갖는다.
 *
 * ⚠️ [refillDays] 는 **이 메모리 대체 버킷 전용**이다. DB 카운터는 (scope, key, KST 날짜) 행이라 **KST 하루 고정**이고
 * 이 값을 읽지 않는다 — `devquest.rate-limit.*.refill-days` 를 바꿔도 정상(DB) 경로의 한도 주기는 달라지지 않는다.
 */
abstract class AbstractRateLimitBucketStore(
    val scope: String,
    val capacity: Long,
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
