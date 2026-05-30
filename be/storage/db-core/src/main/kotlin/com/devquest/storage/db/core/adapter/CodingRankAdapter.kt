package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.port.CodingPassRecord
import com.devquest.core.domain.port.CodingRankPort
import com.devquest.storage.db.core.CodingSubmissionRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class CodingRankAdapter(
    private val submissionRepository: CodingSubmissionRepository
) : CodingRankPort {

    override fun findPassedRecords(userId: String): List<CodingPassRecord> {
        return submissionRepository.findPassedRecordsWithDifficulty(userId).map { row ->
            val problemId = (row[0] as Number).toLong()
            val difficulty = row[1] as String
            val createdAt = row[2] as LocalDateTime
            CodingPassRecord(
                problemId = problemId,
                difficulty = difficulty,
                passedDate = createdAt.toLocalDate()
            )
        }
    }
}
