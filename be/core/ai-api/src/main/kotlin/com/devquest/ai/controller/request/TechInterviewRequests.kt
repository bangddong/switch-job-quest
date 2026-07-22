package com.devquest.ai.controller.request

data class TechInterviewQuestionsRequest(
    val techStack: String,
)

data class TechInterviewEvaluateRequest(
    val techStack: String,
    val questions: List<String>,
    val answers: List<String>,
)

/**
 * `recentQuestions`는 TechInterviewPort.generateDailyQuestion의 Kotlin 기본값(`= emptyList()`)이
 * HTTP JSON에서는 소실되므로 nullable로 받는다 — 컨트롤러가 `?: emptyList()`로 복원한다.
 */
data class TechInterviewDailyQuestionRequest(
    val techStack: String,
    val recentQuestions: List<String>? = null,
)

/**
 * `modelAnswer`는 TechInterviewPort.explainFollowup에서도 이미 nullable(`String? = null`)이라
 * HTTP에서 필드가 빠져도 자연스럽게 null이 된다 — 별도 복원 로직 없이 그대로 전달한다.
 */
data class TechInterviewExplainFollowupRequest(
    val question: String,
    val answer: String,
    val feedback: String,
    val userQuestion: String,
    val modelAnswer: String? = null,
)
