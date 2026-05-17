package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class CodeSubmitRequestDto(
    @field:Positive val problemId: Long = 0,
    @field:NotBlank val language: String = "",
    @field:NotBlank val userCode: String = ""
)
