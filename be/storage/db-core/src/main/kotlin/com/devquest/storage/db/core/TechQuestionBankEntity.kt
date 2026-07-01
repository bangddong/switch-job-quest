package com.devquest.storage.db.core

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "tech_question_bank")
class TechQuestionBankEntity(
    @Column(name = "category", nullable = false, length = 100)
    val category: String = "",

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    val question: String = "",

    @Column(name = "reference_url")
    val referenceUrl: String? = null,

    @Column(name = "source")
    val source: String? = null,
) : BaseEntity()
