package com.devquest.core.domain.model.evaluation

data class InterviewEvaluationResult(
    val questionId: String = "",
    val question: String = "",
    val userAnswer: String = "",
    val score: Int = 0,
    val passed: Boolean = false,
    val technicalAccuracy: Int = 0,
    val depthAndApplication: Int = 0,
    val practicalExperience: Int = 0,
    val communicationClarity: Int = 0,
    val correctAnswer: String = "",
    val keyPointsMissed: List<String> = emptyList(),
    val improvements: String = ""
)
