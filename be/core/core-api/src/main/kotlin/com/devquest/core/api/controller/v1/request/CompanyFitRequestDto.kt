package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CompanyInfoDto(
    val name: String = "",
    val culture: String = "",
    val techStack: List<String> = emptyList(),
    val size: String = "",
    val description: String = ""
)

data class CompanyFitRequestDto(
    @field:NotBlank val userId: String = "",
    @field:Size(min = 1) val preferences: Map<String, String> = emptyMap(),
    @field:Size(min = 1) val companies: List<CompanyInfoDto> = emptyList()
)
