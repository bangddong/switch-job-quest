package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class CoachAnswerRequestDto(
    @field:NotBlank val userId: String,
    @field:NotBlank val question: String,
    @field:NotBlank val answer: String,
    @field:Min(0) val questionIndex: Int,
    @field:Positive val totalQuestions: Int,
)
