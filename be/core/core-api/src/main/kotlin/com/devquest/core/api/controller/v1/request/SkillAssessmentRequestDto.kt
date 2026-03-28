package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SkillAssessmentRequestDto(
    @field:NotBlank val userId: String,
    @field:Size(min = 1, max = 10) val skills: List<String>,
    @field:NotBlank val targetRole: String,
)
