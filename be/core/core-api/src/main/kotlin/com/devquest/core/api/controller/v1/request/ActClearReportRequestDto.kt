package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class ActClearReportRequestDto(
    @field:Positive val actId: Int,
    @field:NotBlank val actTitle: String,
)
