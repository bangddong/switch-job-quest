package com.devquest.core.support.error

import org.springframework.http.HttpStatus

enum class ErrorType(val status: HttpStatus, val code: ErrorCode, val message: String) {
    DEFAULT(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.DEFAULT, ErrorCode.DEFAULT.message),
    AI_EVALUATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.AI_EVALUATION_FAILED, ErrorCode.AI_EVALUATION_FAILED.message),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.message),
    QUEST_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.QUEST_NOT_FOUND, ErrorCode.QUEST_NOT_FOUND.message),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED, ErrorCode.RATE_LIMIT_EXCEEDED.message),
    DAILY_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.DAILY_QUESTION_NOT_FOUND, ErrorCode.DAILY_QUESTION_NOT_FOUND.message),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.COMPANY_NOT_FOUND, ErrorCode.COMPANY_NOT_FOUND.message)
}
