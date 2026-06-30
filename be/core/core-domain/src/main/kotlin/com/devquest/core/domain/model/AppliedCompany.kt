package com.devquest.core.domain.model

import java.time.LocalDateTime

data class AppliedCompany(
    val id: Long = 0L,
    val userId: String = "",
    val companyName: String = "",
    val position: String = "",
    val jdUrl: String? = null,
    val jobDescription: String? = null,
    val status: ApplicationStatus = ApplicationStatus.INTERESTED,
    val notes: String? = null,
    val appliedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
