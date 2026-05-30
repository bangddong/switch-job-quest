package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CodingSubmissionRepository : JpaRepository<CodingSubmissionEntity, Long> {
    fun countByUserIdAndCategoryAndPassed(userId: String, category: String, passed: Boolean): Int

    @Query("SELECT COUNT(DISTINCT s.problemId) FROM CodingSubmissionEntity s WHERE s.userId = :userId AND s.category = :category AND s.passed = true")
    fun countDistinctSolvedProblemsByUserAndCategory(userId: String, category: String): Int
}
