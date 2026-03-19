package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SystemDesignRequestDto(
    @field:NotBlank val userId: String = "",
    @field:NotBlank val questId: String = "",
    @field:NotBlank val problemStatement: String = "",
    @field:NotBlank val architectureDescription: String = "",
    @field:Size(min = 1) val considerations: List<String> = emptyList()
)
