package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.JourneyReportResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface JourneyReportPort : AiEvaluatorPort {
    fun generate(
        companyName: String,
        targetPosition: String,
        questScores: Map<String, Int>,   // questId -> score
        totalXp: Int,
        completedQuestCount: Int,
    ): JourneyReportResult
}
