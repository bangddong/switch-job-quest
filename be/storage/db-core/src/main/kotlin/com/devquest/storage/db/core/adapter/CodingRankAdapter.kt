package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.coding.CodingPassRecord
import com.devquest.core.domain.port.CodingRankPort
import com.devquest.storage.db.core.CodingSubmissionRepository
import org.springframework.stereotype.Component

@Component
class CodingRankAdapter(
    private val submissionRepository: CodingSubmissionRepository
) : CodingRankPort {

    override fun findPassedRecords(userId: String): List<CodingPassRecord> {
        return submissionRepository.findPassedRecordsWithDifficulty(userId).map { row ->
            CodingPassRecord(
                problemId = row.problemId,
                difficulty = row.difficulty,
                passedDate = row.createdAt.toLocalDate()
            )
        }
    }
}
