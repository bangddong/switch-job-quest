package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class DailyQuestionEvaluateRequestDto(
    @field:NotBlank val question: String = "",
    @field:NotBlank val answer: String = "",
)
