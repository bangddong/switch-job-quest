package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.core.domain.port.DailyQuestionContentPort
import com.devquest.storage.db.core.DailyQuestionContentEntity
import com.devquest.storage.db.core.DailyQuestionContentRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class DailyQuestionContentAdapter(
    private val repository: DailyQuestionContentRepository
) : DailyQuestionContentPort {

    override fun findToday(mailType: String, date: LocalDate): DailyQuestionContent? =
        repository.findByMailTypeAndQuestionDate(mailType, date)?.toDomain()

    override fun save(content: DailyQuestionContent): DailyQuestionContent =
        repository.save(content.toEntity()).toDomain()

    override fun findQuestionsSince(mailType: String, since: LocalDate): List<String> =
        repository.findQuestionsSince(mailType, since).distinct()
}

private fun DailyQuestionContentEntity.toDomain() = DailyQuestionContent(
    id = this.id,
    questionDate = this.questionDate,
    mailType = this.mailType,
    question = this.question,
    source = this.source,
    category = this.category,
    createdAt = this.createdAt,
)

private fun DailyQuestionContent.toEntity() = DailyQuestionContentEntity(
    questionDate = this.questionDate,
    mailType = this.mailType,
    question = this.question,
    source = this.source,
    category = this.category,
)
