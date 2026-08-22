package com.devquest.core.domain.port

import com.devquest.core.domain.model.DailyQuestionContent

/**
 * 오늘의 기술 면접 질문 "생성"을 추상화한다.
 *
 * Stage B-1: `DailyQuestionContentService`(구현체)가 `core:daily-core` 라이브러리 모듈로 이동하면서
 * core-api 쪽 소비자(`DailyQuestionService`·`DailyMailScheduler`)가 구체 클래스 대신 이 Port에 의존하도록
 * 분리했다 (원장 L-27 — Port 미주입 위반 해소).
 */
interface DailyQuestionGeneratorPort {
    /**
     * 오늘의 질문이 이미 있으면 그대로 반환하고(멱등), 없으면 뱅크 → AI 순서로 골라 저장한다.
     */
    fun ensureTodayQuestion(): DailyQuestionContent

    /**
     * 오늘의 질문을 뱅크에서만 조회/생성한다 — AI 폴백 없음. 뱅크가 소진되면 `null`을 반환한다.
     * 읽기 경로(`GET /api/v1/daily-question`) 전용.
     */
    fun ensureTodayQuestionFromBank(): DailyQuestionContent?
}
