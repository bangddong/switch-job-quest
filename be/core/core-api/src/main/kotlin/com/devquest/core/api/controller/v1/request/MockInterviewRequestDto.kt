package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class MockInterviewRequestDto(
    val userId: String = "",
    val questId: String = "2-BOSS",
    @field:NotBlank val questionId: String = "",
    @field:NotBlank val question: String = "",
    @field:NotBlank val answer: String = "",
    @field:NotBlank val category: String = ""
)
