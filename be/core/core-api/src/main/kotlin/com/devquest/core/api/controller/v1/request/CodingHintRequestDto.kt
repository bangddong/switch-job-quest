package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class CodingHintRequestDto(
    @field:Positive val problemId: Long = 0L,
    @field:NotBlank val title: String = "",
    @field:NotBlank val description: String = "",
    @field:Min(1) @field:Max(3) val hintLevel: Int = 0
)
