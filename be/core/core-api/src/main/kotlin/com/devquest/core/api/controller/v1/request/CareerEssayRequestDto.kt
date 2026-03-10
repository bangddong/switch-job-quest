package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CareerEssayRequestDto(
    val userId: String = "",
    @field:Size(min = 3, max = 5) val dissatisfactions: List<String> = emptyList(),
    @field:Size(min = 3, max = 5) val goals: List<String> = emptyList(),
    @field:NotBlank val fiveYearVision: String = ""
)
