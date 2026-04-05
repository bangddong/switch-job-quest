package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CoachAnswerHistoryDto(
    val question: String,
    val answer: String,
    val feedback: String,
)

data class CoachReportRequestDto(
    @field:NotBlank val targetRole: String,
    @field:NotBlank val jdSummary: String,
    @field:Size(min = 1, max = 10) val answers: List<CoachAnswerHistoryDto>,
)
