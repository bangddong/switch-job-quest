package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.ActivityType
import com.devquest.core.domain.model.CompanyActivity
import com.devquest.core.domain.port.CompanyActivityPort
import com.devquest.storage.db.core.CompanyActivityEntity
import com.devquest.storage.db.core.CompanyActivityRepository
import org.springframework.stereotype.Component

@Component
class CompanyActivityAdapter(
    private val repository: CompanyActivityRepository
) : CompanyActivityPort {

    override fun save(activity: CompanyActivity): CompanyActivity {
        return repository.save(activity.toEntity()).toDomain()
    }

    override fun findLatestByCompanyIdAndType(companyId: Long, type: ActivityType): CompanyActivity? {
        return repository.findTopByCompanyIdAndActivityTypeOrderByCreatedAtDesc(companyId, type)?.toDomain()
    }

    override fun findAllByCompanyId(companyId: Long): List<CompanyActivity> {
        return repository.findAllByCompanyIdOrderByCreatedAtDesc(companyId).map { it.toDomain() }
    }

    private fun CompanyActivityEntity.toDomain(): CompanyActivity {
        return CompanyActivity(
            id = this.id,
            companyId = this.companyId,
            userId = this.userId,
            activityType = this.activityType,
            aiScore = this.aiScore,
            aiResultJson = this.aiResultJson,
            createdAt = this.createdAt,
        )
    }

    private fun CompanyActivity.toEntity(): CompanyActivityEntity {
        return CompanyActivityEntity(
            companyId = this.companyId,
            userId = this.userId,
            activityType = this.activityType,
            aiScore = this.aiScore,
            aiResultJson = this.aiResultJson,
        )
    }
}
