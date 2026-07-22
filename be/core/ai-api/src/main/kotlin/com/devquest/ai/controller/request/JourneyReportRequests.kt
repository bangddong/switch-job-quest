package com.devquest.ai.controller.request

data class JourneyReportGenerateRequest(
    val companyName: String,
    val targetPosition: String,
    val questScores: Map<String, Int>,
    val totalXp: Int,
    val completedQuestCount: Int,
)
