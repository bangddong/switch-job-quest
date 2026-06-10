package com.devquest.core.support.error

enum class ErrorCode(val message: String) {
    DEFAULT("알 수 없는 오류가 발생했습니다"),
    AI_EVALUATION_FAILED("AI 평가 중 오류가 발생했습니다"),
    INVALID_REQUEST("유효하지 않은 요청입니다"),
    QUEST_NOT_FOUND("퀘스트를 찾을 수 없습니다"),
    RATE_LIMIT_EXCEEDED("오늘 체험 횟수를 초과했습니다. 내일 다시 시도해주세요.")
}
