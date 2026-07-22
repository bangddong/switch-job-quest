package com.devquest.ai.controller.request

data class InterviewEvaluateRequest(
    val category: String,
    val question: String,
    val answer: String,
    val questionId: String,
    val techStack: List<String>,
    val yearsOfExperience: String,
)

data class InterviewGenerateQuestionsRequest(
    val techStack: List<String>,
    val targetRole: String,
    val yearsOfExperience: String,
    val categories: List<String>,
    val personalityCount: Int,
    val techCount: Int,
)
