package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class PersonalityInterviewRequestDto(
    val userId: String = "",
    @field:NotBlank val question: String = "",
    @field:NotBlank val answer: String = ""
)
