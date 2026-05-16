package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.port.UserEmailPort
import com.devquest.storage.db.core.UserEmailEntity
import com.devquest.storage.db.core.UserEmailRepository
import org.springframework.stereotype.Component

@Component
class UserEmailAdapter(
    private val repository: UserEmailRepository
) : UserEmailPort {

    override fun save(userId: String, email: String) {
        val existing = repository.findById(userId).orElse(null)
        if (existing != null) {
            existing.email = email
            repository.save(existing)
        } else {
            repository.save(UserEmailEntity(userId = userId, email = email))
        }
    }

    override fun findByUserId(userId: String): String? {
        return repository.findById(userId).orElse(null)?.email
    }

    override fun findAll(): List<Pair<String, String>> {
        return repository.findAll().map { Pair(it.userId, it.email) }
    }
}
