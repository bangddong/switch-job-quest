package com.devquest.core.domain.model.evaluation

data class JourneyReportResult(
    val companyName: String = "",
    val targetPosition: String = "",
    val totalXp: Int = 0,
    val completedQuestCount: Int = 0,
    val narrative: String = "",         // 감성적 회고 내러티브 (3~4 문단)
    val lowestQuestId: String = "",     // 가장 힘들었던 퀘스트
    val highestQuestId: String = "",    // 가장 빛났던 퀘스트
    val finalMessage: String = "",      // 마지막 한 마디
)
