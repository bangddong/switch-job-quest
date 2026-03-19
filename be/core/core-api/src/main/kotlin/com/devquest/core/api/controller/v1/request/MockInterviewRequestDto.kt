package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class MockInterviewRequestDto(
    @field:NotBlank val userId: String = "",
    @field:NotBlank val questId: String = "",
    @field:NotBlank val questionId: String = "",
    @field:NotBlank val question: String = "",
    @field:NotBlank val answer: String = "",
    @field:NotBlank val category: String = ""
)
