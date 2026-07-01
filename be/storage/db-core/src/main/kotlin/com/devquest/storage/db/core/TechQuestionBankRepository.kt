package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface TechQuestionBankRepository : JpaRepository<TechQuestionBankEntity, Long> {
    fun findFirstByCategoryAndQuestionNotInOrderByIdAsc(
        category: String,
        excludeQuestions: List<String>,
    ): TechQuestionBankEntity?

    fun findFirstByQuestionNotInOrderByIdAsc(excludeQuestions: List<String>): TechQuestionBankEntity?

    fun findFirstByCategoryOrderByIdAsc(category: String): TechQuestionBankEntity?

    fun findFirstByOrderByIdAsc(): TechQuestionBankEntity?
}
