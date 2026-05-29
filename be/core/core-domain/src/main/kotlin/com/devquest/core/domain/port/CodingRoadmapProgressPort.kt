package com.devquest.core.domain.port

interface CodingRoadmapProgressPort {
    fun countSolvedByUserAndCategory(userId: String, category: String): Int
}
