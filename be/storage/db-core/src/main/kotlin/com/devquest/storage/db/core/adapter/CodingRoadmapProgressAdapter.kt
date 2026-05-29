package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.port.CodingRoadmapProgressPort
import com.devquest.storage.db.core.CodingSubmissionRepository
import org.springframework.stereotype.Component

@Component
class CodingRoadmapProgressAdapter(
    private val codingSubmissionRepository: CodingSubmissionRepository
) : CodingRoadmapProgressPort {

    override fun countSolvedByUserAndCategory(userId: String, category: String): Int =
        codingSubmissionRepository.countByUserIdAndCategoryAndPassed(userId, category, true)
}
