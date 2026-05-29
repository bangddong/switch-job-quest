package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface CodingProblemRepository : JpaRepository<CodingProblemEntity, Long> {
    fun findByDifficultyAndLanguage(difficulty: String, language: String): List<CodingProblemEntity>
    fun findByCategoryAndLanguage(category: String, language: String): List<CodingProblemEntity>
}
