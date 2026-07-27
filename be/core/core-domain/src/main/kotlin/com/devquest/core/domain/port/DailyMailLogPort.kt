package com.devquest.core.domain.port

import java.time.LocalDate
import java.time.LocalDateTime

interface DailyMailLogPort {
    fun save(userId: String, mailType: String, questionContent: String, sentAt: LocalDateTime)
    fun existsTodayLog(userId: String, mailType: String, date: LocalDate): Boolean
    fun findQuestionsSince(mailType: String, since: LocalDateTime): List<String>
    fun findTodayQuestion(mailType: String, date: LocalDate): String?
}
