package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface DailyQuestionContentRepository : JpaRepository<DailyQuestionContentEntity, Long> {
    fun findByMailTypeAndQuestionDate(mailType: String, questionDate: LocalDate): DailyQuestionContentEntity?

    @Query(
        """
        SELECT d.question FROM DailyQuestionContentEntity d
        WHERE d.mailType = :mailType AND d.questionDate >= :since
        ORDER BY d.questionDate DESC
        """
    )
    fun findQuestionsSince(
        @Param("mailType") mailType: String,
        @Param("since") since: LocalDate,
    ): List<String>
}
