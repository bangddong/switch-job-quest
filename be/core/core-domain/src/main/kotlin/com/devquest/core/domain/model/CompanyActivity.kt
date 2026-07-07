package com.devquest.core.domain.model

import java.time.LocalDateTime

data class CompanyActivity(
    val id: Long = 0L,
    val companyId: Long = 0L,
    val userId: String = "",
    val activityType: ActivityType = ActivityType.JD_ANALYSIS,
    val aiScore: Int = 0,
    val aiResultJson: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

enum class ActivityType {
    JD_ANALYSIS,
    RESUME_CHECK,
    TECH_INTERVIEW,
    HR_INTERVIEW,
    BOSS_PACKAGE,
    STATUS_CHANGE,
}
