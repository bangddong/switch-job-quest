package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class JdAnalysisRequestDto(
    @field:NotBlank val companyName: String = "",
    @field:NotBlank val jobDescription: String = "",
    @field:Size(min = 1) val userSkills: List<String> = emptyList(),
    @field:Size(min = 1) val userExperiences: List<String> = emptyList()
)
