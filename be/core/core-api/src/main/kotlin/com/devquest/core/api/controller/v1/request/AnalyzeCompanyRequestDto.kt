package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotEmpty

data class AnalyzeCompanyRequestDto(
    @field:NotEmpty val userSkills: List<String> = emptyList(),
    @field:NotEmpty val userExperiences: List<String> = emptyList(),
)
