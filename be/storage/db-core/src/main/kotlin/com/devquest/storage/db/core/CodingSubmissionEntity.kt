package com.devquest.storage.db.core

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "coding_submission")
class CodingSubmissionEntity(
    @Column(nullable = false)
    val userId: String = "",

    @Column(nullable = false)
    val problemId: Long = 0,

    @Column(nullable = false, length = 20)
    val language: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    val userCode: String = "",

    @Column(nullable = false)
    var passed: Boolean = false,

    @Column(columnDefinition = "TEXT")
    val judgeResult: String? = null
) : BaseEntity()
