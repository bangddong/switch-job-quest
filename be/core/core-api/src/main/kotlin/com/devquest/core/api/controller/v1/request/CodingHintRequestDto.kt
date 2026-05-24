package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CodingHintRequestDto(
    @field:NotBlank val problemId: String = "",
    @field:NotBlank val title: String = "",
    @field:NotBlank val description: String = "",
    @field:Min(1) @field:Max(3) val hintLevel: Int = 1
)
