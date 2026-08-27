package com.devquest.daily.controller.request

import jakarta.validation.constraints.NotBlank

/** core-api `DailyQuestionEvaluateRequestDto`와 동일 제약 (D-007 — 별개 클래스로 소유). */
data class DailyQuestionEvaluateRequestDto(
    @field:NotBlank val question: String = "",
    @field:NotBlank val answer: String = "",
)
