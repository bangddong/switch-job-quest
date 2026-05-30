package com.devquest.core.domain.model.coding

import java.time.LocalDate

data class CodingPassRecord(
    val problemId: Long,
    val difficulty: String,
    val passedDate: LocalDate
)
