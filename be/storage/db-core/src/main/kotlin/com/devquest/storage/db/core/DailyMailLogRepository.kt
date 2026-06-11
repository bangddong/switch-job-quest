package com.devquest.storage.db.core

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface DailyMailLogRepository : JpaRepository<DailyMailLogEntity, Long> {
    fun existsByUserIdAndMailTypeAndSentAtBetween(
        userId: String,
        mailType: String,
        start: LocalDateTime,
        end: LocalDateTime,
    ): Boolean

    @Query("SELECT d.questionContent FROM DailyMailLogEntity d WHERE d.mailType = :mailType ORDER BY d.sentAt DESC")
    fun findRecentQuestionContents(mailType: String, pageable: Pageable): List<String>

    @Query("""
        SELECT d.questionContent FROM DailyMailLogEntity d
        WHERE d.mailType = :mailType
          AND d.sentAt >= :start AND d.sentAt < :end
        ORDER BY d.sentAt DESC
    """)
    fun findTodayQuestionContent(
        @Param("mailType") mailType: String,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime,
        pageable: Pageable,
    ): List<String>
}
