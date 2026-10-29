package com.devquest.core.domain.port

/**
 * 레이트 리밋 카운터의 **공유 저장소** 포트 (U-6).
 *
 * 한도는 (scope, key, KST 날짜) 단위로 하루 `capacity` 회다. 구현은 여러 인스턴스(Fly·EKS 파드, core-api·daily-api)가
 * 같은 저장소를 보게 해서 "인스턴스마다 한도가 따로 도는" 문제를 없앤다. 날짜가 키의 일부라 자정 리셋이 필요 없다.
 *
 * - [key] 는 원문(IP·사용자 ID)을 넘긴다. 저장 전 해시는 구현의 책임이다 — 호출처(앱마다 복제된 인터셉터)가
 *   해시 규칙을 따로 갖지 않게 한 곳에 둔다.
 * - 실패하면 예외를 던진다. 대체 경로(메모리 버킷)로의 전환은 호출처의 정책이다(fail-soft).
 */
interface RateLimitCounterPort {

    /**
     * 오늘(KST) 사용량이 [capacity] 미만일 때만 원자적으로 1 증가시킨다.
     * 동시에 여러 요청이 와도 정확히 [capacity] 건만 true 를 받는다.
     *
     * @return true = 차감됨(요청 허용), false = 상한 도달(요청 거부, 사용량 불변)
     */
    fun tryConsume(scope: String, key: String, capacity: Int): Boolean

    /** 오늘(KST) 사용량을 1 되돌린다. 0 아래로 내려가지 않는다(행이 없거나 0 이면 아무 일도 없다). */
    fun refund(scope: String, key: String)

    /** 보관 기간(2일)이 지난 행을 지운다. 여러 인스턴스가 동시에 돌려도 안전한 멱등 삭제다. @return 지운 행 수 */
    fun deleteExpired(): Int
}
