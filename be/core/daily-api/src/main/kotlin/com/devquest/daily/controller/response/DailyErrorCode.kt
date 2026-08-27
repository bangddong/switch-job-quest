package com.devquest.daily.controller.response

import org.springframework.http.HttpStatus

/**
 * daily-api 자체 에러 코드 (D-007 — core-api의 `ErrorCode`/`ErrorType`을 재사용할 수 없다).
 */
enum class DailyErrorCode(val status: HttpStatus, val message: String) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    DAILY_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "오늘의 질문을 찾을 수 없습니다."),
    AI_EVALUATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 평가에 실패했습니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청 한도를 초과했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
}
