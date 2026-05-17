package com.devquest.core.domain.port

interface UserEmailPort {
    fun save(userId: String, email: String)
    fun findByUserId(userId: String): String?
    fun findAll(): List<Pair<String, String>>
}
