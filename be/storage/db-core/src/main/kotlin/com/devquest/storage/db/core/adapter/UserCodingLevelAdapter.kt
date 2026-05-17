package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.port.UserCodingLevelPort
import com.devquest.storage.db.core.UserCodingLevelEntity
import com.devquest.storage.db.core.UserCodingLevelRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class UserCodingLevelAdapter(
    private val repository: UserCodingLevelRepository
) : UserCodingLevelPort {

    override fun getLevel(userId: String): Int {
        return repository.findById(userId).orElse(null)?.level ?: 1
    }

    override fun getSolveCount(userId: String): Int {
        return repository.findById(userId).orElse(null)?.solveCount ?: 0
    }

    override fun incrementLevel(userId: String) {
        val entity = repository.findById(userId).orElse(UserCodingLevelEntity(userId = userId))
        entity.level = minOf(entity.level + 1, 10)
        entity.updatedAt = LocalDateTime.now()
        repository.save(entity)
    }

    override fun incrementSolveCount(userId: String) {
        val entity = repository.findById(userId).orElse(UserCodingLevelEntity(userId = userId))
        entity.solveCount = entity.solveCount + 1
        entity.updatedAt = LocalDateTime.now()
        repository.save(entity)
    }
}
