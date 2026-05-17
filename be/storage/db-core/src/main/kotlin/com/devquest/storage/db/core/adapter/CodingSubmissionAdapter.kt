package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.port.CodingSubmissionPort
import com.devquest.storage.db.core.CodingSubmissionEntity
import com.devquest.storage.db.core.CodingSubmissionRepository
import org.springframework.stereotype.Component

@Component
class CodingSubmissionAdapter(
    private val repository: CodingSubmissionRepository
) : CodingSubmissionPort {

    override fun save(
        userId: String,
        problemId: Long,
        language: String,
        userCode: String,
        passed: Boolean,
        judgeResult: String
    ): Long {
        val entity = CodingSubmissionEntity(
            userId = userId,
            problemId = problemId,
            language = language,
            userCode = userCode,
            passed = passed,
            judgeResult = judgeResult
        )
        return repository.save(entity).id
    }
}
