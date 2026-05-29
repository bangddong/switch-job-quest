package com.devquest.storage.db.core

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "coding_problem")
class CodingProblemEntity(
    @Column(nullable = false)
    val title: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    val description: String = "",

    @Column(nullable = false, length = 20)
    val difficulty: String = "",

    @Column(nullable = false, length = 20)
    val language: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    val solutionCode: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    val testCases: String = "",

    @Column(nullable = true, length = 50)
    val category: String? = null
) : BaseEntity()
