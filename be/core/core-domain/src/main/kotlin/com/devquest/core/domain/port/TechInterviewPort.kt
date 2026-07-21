package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface TechInterviewPort : AiEvaluatorPort {
    fun generateQuestions(techStack: String): TechInterviewResult
    fun evaluate(techStack: String, questions: List<String>, answers: List<String>): TechInterviewResult
    fun generateDailyQuestion(techStack: String, recentQuestions: List<String> = emptyList()): String
    fun explainFollowup(
        question: String,
        answer: String,
        feedback: String,
        userQuestion: String,
        modelAnswer: String? = null,
    ): String
}
