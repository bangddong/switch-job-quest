package com.devquest.core.domain.port

import java.time.LocalDate
import java.time.LocalDateTime

interface DailyMailLogPort {
    fun save(userId: String, mailType: String, questionContent: String, sentAt: LocalDateTime)
    fun existsTodayLog(userId: String, mailType: String, date: LocalDate): Boolean
    fun findRecentQuestions(mailType: String, limit: Int): List<String>
}
