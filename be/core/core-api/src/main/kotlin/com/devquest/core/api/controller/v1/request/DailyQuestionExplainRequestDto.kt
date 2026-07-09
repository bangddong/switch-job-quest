package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class DailyQuestionExplainRequestDto(
    @field:NotBlank @field:Size(max = 500) val question: String = "",
    @field:NotBlank @field:Size(max = 3000) val answer: String = "",
    @field:NotBlank @field:Size(max = 3000) val feedback: String = "",
    @field:NotBlank @field:Size(max = 1000) val userQuestion: String = "",
    @field:Size(max = 3000) val modelAnswer: String? = null,
)
