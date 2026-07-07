package com.devquest.core.domain.port

import com.devquest.core.domain.model.ActivityType
import com.devquest.core.domain.model.CompanyActivity

interface CompanyActivityPort {
    fun save(activity: CompanyActivity): CompanyActivity
    fun findLatestByCompanyIdAndType(companyId: Long, type: ActivityType): CompanyActivity?
    fun findAllByCompanyId(companyId: Long): List<CompanyActivity>
}
