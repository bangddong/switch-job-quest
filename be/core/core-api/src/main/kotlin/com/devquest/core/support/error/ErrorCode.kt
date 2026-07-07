package com.devquest.core.support.error

enum class ErrorCode(val message: String) {
    DEFAULT("알 수 없는 오류가 발생했습니다"),
    AI_EVALUATION_FAILED("AI 평가 중 오류가 발생했습니다"),
    INVALID_REQUEST("유효하지 않은 요청입니다"),
    QUEST_NOT_FOUND("퀘스트를 찾을 수 없습니다"),
    RATE_LIMIT_EXCEEDED("오늘 체험 횟수를 초과했습니다. 내일 다시 시도해주세요."),
    DAILY_QUESTION_NOT_FOUND("오늘의 질문을 아직 준비 중입니다. 오전 9시 이후 다시 확인해주세요."),
    COMPANY_NOT_FOUND("지원 회사를 찾을 수 없습니다"),
    COMPANY_JD_NOT_REGISTERED("등록된 채용공고(JD)가 없습니다. 회사 정보에 JD를 먼저 등록해주세요."),
    RESUME_NOT_REGISTERED("등록된 이력서가 없습니다. 이력서를 먼저 등록해주세요.")
}
