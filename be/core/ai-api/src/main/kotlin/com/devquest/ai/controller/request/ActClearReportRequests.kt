package com.devquest.ai.controller.request

data class ActClearReportGenerateRequest(
    val actId: Int,
    val actTitle: String,
    val questScores: Map<String, Int>,
)
