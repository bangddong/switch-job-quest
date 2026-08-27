package com.devquest.daily.controller.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** core-api `DailyQuestionExplainRequestDto`와 동일 제약 (D-007 — 별개 클래스로 소유). */
data class DailyQuestionExplainRequestDto(
    @field:NotBlank @field:Size(max = 500) val question: String = "",
    @field:NotBlank @field:Size(max = 3000) val answer: String = "",
    @field:NotBlank @field:Size(max = 3000) val feedback: String = "",
    @field:NotBlank @field:Size(max = 1000) val userQuestion: String = "",
    @field:Size(max = 3000) val modelAnswer: String? = null,
)
