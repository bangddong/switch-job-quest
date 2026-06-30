package com.devquest.core.api.controller.v1.request

import com.devquest.core.domain.model.ApplicationStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class CreateCompanyRequestDto(
    @field:NotBlank val companyName: String = "",
    val position: String = "",
    val jdUrl: String? = null,
    val jobDescription: String? = null,
)

data class UpdateCompanyStatusRequestDto(
    @field:NotNull val status: ApplicationStatus = ApplicationStatus.INTERESTED,
    val appliedAt: LocalDateTime? = null,
)
