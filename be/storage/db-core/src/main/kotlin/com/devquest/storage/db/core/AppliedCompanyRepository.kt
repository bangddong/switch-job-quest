package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface AppliedCompanyRepository : JpaRepository<AppliedCompanyEntity, Long> {
    fun findAllByUserId(userId: String): List<AppliedCompanyEntity>
    fun findByIdAndUserId(id: Long, userId: String): AppliedCompanyEntity?
    fun deleteByIdAndUserId(id: Long, userId: String)
}
