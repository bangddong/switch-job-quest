package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class BossPackageRequestDto(
    @field:NotBlank val resumeContent: String = "",
    @field:NotBlank val githubUrl: String = "",
    @field:NotBlank val targetPosition: String = "",
    val blogUrl: String = ""
)
