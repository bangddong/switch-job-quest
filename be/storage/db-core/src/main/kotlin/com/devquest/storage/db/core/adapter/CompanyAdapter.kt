package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.AppliedCompany
import com.devquest.core.domain.port.CompanyPort
import com.devquest.storage.db.core.AppliedCompanyEntity
import com.devquest.storage.db.core.AppliedCompanyRepository
import org.springframework.stereotype.Component

@Component
class CompanyAdapter(
    private val repository: AppliedCompanyRepository
) : CompanyPort {

    override fun save(company: AppliedCompany): AppliedCompany {
        val entity = if (company.id != 0L) {
            repository.findById(company.id).orElse(null)?.apply {
                position = company.position
                jdUrl = company.jdUrl
                jobDescription = company.jobDescription
                status = company.status
                notes = company.notes
                appliedAt = company.appliedAt
            } ?: toEntity(company)
        } else {
            toEntity(company)
        }
        return repository.save(entity).toDomain()
    }

    override fun findAllByUserId(userId: String): List<AppliedCompany> {
        return repository.findAllByUserId(userId).map { it.toDomain() }
    }

    override fun findByIdAndUserId(id: Long, userId: String): AppliedCompany? {
        return repository.findByIdAndUserId(id, userId)?.toDomain()
    }

    override fun delete(id: Long, userId: String) {
        repository.deleteByIdAndUserId(id, userId)
    }

    private fun AppliedCompanyEntity.toDomain(): AppliedCompany {
        return AppliedCompany(
            id = this.id,
            userId = this.userId,
            companyName = this.companyName,
            position = this.position,
            jdUrl = this.jdUrl,
            jobDescription = this.jobDescription,
            status = this.status,
            notes = this.notes,
            appliedAt = this.appliedAt,
            createdAt = this.createdAt,
        )
    }

    private fun toEntity(company: AppliedCompany): AppliedCompanyEntity {
        return AppliedCompanyEntity(
            userId = company.userId,
            companyName = company.companyName,
            position = company.position,
            jdUrl = company.jdUrl,
            jobDescription = company.jobDescription,
            status = company.status,
            notes = company.notes,
            appliedAt = company.appliedAt,
        )
    }
}
