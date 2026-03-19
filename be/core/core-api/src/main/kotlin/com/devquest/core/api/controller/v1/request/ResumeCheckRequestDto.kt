package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class ResumeCheckRequestDto(
    @field:NotBlank val userId: String = "",
    @field:NotBlank val targetCompany: String = "",
    @field:NotBlank val targetJd: String = "",
    @field:NotBlank val resumeContent: String = ""
)
