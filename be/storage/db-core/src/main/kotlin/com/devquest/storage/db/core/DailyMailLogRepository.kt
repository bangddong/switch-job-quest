package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface DailyMailLogRepository : JpaRepository<DailyMailLogEntity, Long> {
    fun existsByUserIdAndMailTypeAndSentAtBetween(
        userId: String,
        mailType: String,
        start: LocalDateTime,
        end: LocalDateTime,
    ): Boolean
}
