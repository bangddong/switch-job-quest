package com.devquest.storage.db.core

import com.devquest.core.domain.model.ActivityType
import jakarta.persistence.*

@Entity
@Table(name = "company_activity")
class CompanyActivityEntity(
    @Column(nullable = false)
    val companyId: Long = 0L,

    @Column(nullable = false)
    val userId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val activityType: ActivityType = ActivityType.JD_ANALYSIS,

    @Column(nullable = false)
    val aiScore: Int = 0,

    @Column(columnDefinition = "TEXT", nullable = false)
    val aiResultJson: String = "",
) : BaseEntity()
