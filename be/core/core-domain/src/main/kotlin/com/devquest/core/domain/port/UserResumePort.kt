package com.devquest.core.domain.port

import com.devquest.core.domain.model.UserResume

interface UserResumePort {
    fun findByUserId(userId: String): UserResume?
    fun save(resume: UserResume): UserResume
}
