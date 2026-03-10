package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class ResumeCheckRequestDto(
    val userId: String = "",
    @field:NotBlank val targetCompany: String = "",
    @field:NotBlank val targetJd: String = "",
    @field:NotBlank val resumeContent: String = ""
)
