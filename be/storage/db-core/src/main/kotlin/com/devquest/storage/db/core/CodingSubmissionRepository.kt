package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CodingSubmissionRepository : JpaRepository<CodingSubmissionEntity, Long> {
    fun countByUserIdAndCategoryAndPassed(userId: String, category: String, passed: Boolean): Int

    @Query("SELECT COUNT(DISTINCT s.problemId) FROM CodingSubmissionEntity s WHERE s.userId = :userId AND s.category = :category AND s.passed = true")
    fun countDistinctSolvedProblemsByUserAndCategory(userId: String, category: String): Int

    @Query("""
        SELECT s.problemId, p.difficulty, s.createdAt
        FROM CodingSubmissionEntity s
        JOIN CodingProblemEntity p ON s.problemId = p.id
        WHERE s.userId = :userId AND s.passed = true
    """)
    fun findPassedRecordsWithDifficulty(userId: String): List<Array<Any>>
}
