package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface TechQuestionBankRepository : JpaRepository<TechQuestionBankEntity, Long> {
    fun findAllByCategoryAndQuestionNotIn(
        category: String,
        excludeQuestions: List<String>,
    ): List<TechQuestionBankEntity>

    fun findAllByQuestionNotIn(excludeQuestions: List<String>): List<TechQuestionBankEntity>

    fun findAllByCategory(category: String): List<TechQuestionBankEntity>
}
