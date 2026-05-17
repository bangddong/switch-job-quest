package com.devquest.core.domain.port

interface UserCodingLevelPort {
    fun getLevel(userId: String): Int
    fun incrementLevel(userId: String)
    fun incrementSolveCount(userId: String)
    fun getSolveCount(userId: String): Int
}
