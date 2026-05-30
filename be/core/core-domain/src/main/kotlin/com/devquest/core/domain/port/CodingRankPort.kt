package com.devquest.core.domain.port

import java.time.LocalDate

data class CodingPassRecord(
    val problemId: Long,
    val difficulty: String,
    val passedDate: LocalDate
)

interface CodingRankPort {
    fun findPassedRecords(userId: String): List<CodingPassRecord>
}
