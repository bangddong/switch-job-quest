package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface UserResumeRepository : JpaRepository<UserResumeEntity, Long> {
    fun findByUserId(userId: String): UserResumeEntity?
}
