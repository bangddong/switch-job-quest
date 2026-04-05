package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class JourneyReportRequestDto(
    @field:NotBlank val companyName: String,
    @field:NotBlank val targetPosition: String,
)
