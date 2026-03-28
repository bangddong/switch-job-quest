package com.devquest.core.domain.model.evaluation

data class ActClearReportResult(
    val actId: Int = 0,
    val actTitle: String = "",
    val overallScore: Int = 0,
    val grade: String = "D",
    val developerClass: String = "",
    val achievements: List<String> = emptyList(),
    val nextActHint: String = "",
    val encouragement: String = "",
)
