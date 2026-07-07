package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.UserResume
import com.devquest.core.domain.port.UserResumePort
import com.devquest.storage.db.core.UserResumeEntity
import com.devquest.storage.db.core.UserResumeRepository
import org.springframework.stereotype.Component

@Component
class UserResumeAdapter(
    private val repository: UserResumeRepository
) : UserResumePort {

    override fun findByUserId(userId: String): UserResume? {
        return repository.findByUserId(userId)?.toDomain()
    }

    override fun save(resume: UserResume): UserResume {
        val existing = repository.findByUserId(resume.userId)
        val entity = if (existing != null) {
            existing.apply { content = resume.content }
        } else {
            UserResumeEntity(userId = resume.userId, content = resume.content)
        }
        return repository.save(entity).toDomain()
    }

    private fun UserResumeEntity.toDomain(): UserResume {
        return UserResume(
            id = this.id,
            userId = this.userId,
            content = this.content,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
