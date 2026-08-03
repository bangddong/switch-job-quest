package com.devquest.storage.db.core

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "daily_question_content",
    uniqueConstraints = [UniqueConstraint(columnNames = ["question_date", "mail_type"])]
)
class DailyQuestionContentEntity(
    @Column(name = "question_date", nullable = false)
    val questionDate: LocalDate = LocalDate.now(),

    @Column(name = "mail_type", nullable = false, length = 50)
    val mailType: String = "",

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    val question: String = "",

    @Column(name = "source", nullable = false, length = 20)
    val source: String = "",

    @Column(name = "category", length = 100)
    val category: String? = null,
) : BaseEntity()
