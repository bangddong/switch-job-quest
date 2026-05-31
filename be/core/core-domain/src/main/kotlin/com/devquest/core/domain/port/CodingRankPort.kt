package com.devquest.core.domain.port

import com.devquest.core.domain.model.coding.CodingPassRecord

interface CodingRankPort {
    fun findPassedRecords(userId: String): List<CodingPassRecord>
    fun countSolvedByCategory(userId: String, category: String): Int
}
