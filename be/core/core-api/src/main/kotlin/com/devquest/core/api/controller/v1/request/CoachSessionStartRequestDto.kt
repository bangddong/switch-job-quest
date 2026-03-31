package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CoachSessionStartRequestDto(
    @field:NotBlank val userId: String,
    @field:NotBlank @field:Size(min = 10, max = 5000) val jdText: String,
    @field:NotBlank val targetRole: String,
)
