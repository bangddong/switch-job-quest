package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ResumeRequestDto(
    @field:NotBlank
    @field:Size(max = 50_000)
    val content: String = "",
)
