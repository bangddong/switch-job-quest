package com.devquest.storage.db.core

import com.devquest.core.domain.model.ActivityType
import org.springframework.data.jpa.repository.JpaRepository

interface CompanyActivityRepository : JpaRepository<CompanyActivityEntity, Long> {
    fun findTopByCompanyIdAndActivityTypeOrderByCreatedAtDesc(
        companyId: Long,
        activityType: ActivityType,
    ): CompanyActivityEntity?

    fun findAllByCompanyIdOrderByCreatedAtDesc(companyId: Long): List<CompanyActivityEntity>
}
