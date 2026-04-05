package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class PersonalityInterviewRequestDto(
    @field:NotBlank val question: String = "",
    @field:NotBlank val answer: String = ""
)
