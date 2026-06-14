package com.devquest.core.api.config

/**
 * 데일리 기술면접 질문 설정.
 * Spring 의존성 없는 순수 데이터 클래스 — 빈 등록은 DailyQuestionBeanConfig에서 담당.
 */
data class DailyQuestionProperties(
    val techStack: String,
)
