package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.port.DailyMailLogPort
import com.devquest.storage.db.core.DailyMailLogEntity
import com.devquest.storage.db.core.DailyMailLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class DailyMailLogAdapter(
    private val repository: DailyMailLogRepository
) : DailyMailLogPort {

    override fun save(userId: String, mailType: String, questionContent: String, sentAt: LocalDateTime) {
        repository.save(
            DailyMailLogEntity(
                userId = userId,
                mailType = mailType,
                questionContent = questionContent,
                sentAt = sentAt,
            )
        )
    }

    override fun existsTodayLog(userId: String, mailType: String, date: LocalDate): Boolean {
        val start = date.atStartOfDay()
        val end = date.plusDays(1).atStartOfDay()
        return repository.existsByUserIdAndMailTypeAndSentAtBetween(userId, mailType, start, end)
    }

    override fun findRecentQuestions(mailType: String, limit: Int): List<String> =
        repository.findRecentQuestionContents(mailType, PageRequest.of(0, limit))
}
