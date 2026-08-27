package com.devquest.daily.support

/**
 * daily-api는 core-api의 `CoreException`/`ErrorType`을 쓸 수 없다(D-007 — core-api 의존 금지).
 * `GET /api/v1/daily-question`에서 오늘 행도 없고 뱅크도 소진이면 이 예외를 던지고,
 * `DailyApiControllerAdvice`가 404로 매핑한다.
 */
class DailyQuestionNotFoundException : RuntimeException("오늘의 질문을 찾을 수 없습니다.")
