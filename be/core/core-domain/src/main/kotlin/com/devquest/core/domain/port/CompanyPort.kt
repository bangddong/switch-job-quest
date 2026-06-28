package com.devquest.core.domain.port

import com.devquest.core.domain.model.AppliedCompany

interface CompanyPort {
    fun save(company: AppliedCompany): AppliedCompany
    fun findAllByUserId(userId: String): List<AppliedCompany>
    fun findByIdAndUserId(id: Long, userId: String): AppliedCompany?
    fun delete(id: Long, userId: String)
}
