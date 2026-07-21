package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.InterviewEvaluationResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface InterviewEvaluatorPort : AiEvaluatorPort {
    fun evaluate(
        category: String,
        question: String,
        answer: String,
        questionId: String,
        techStack: List<String>,
        yearsOfExperience: String
    ): InterviewEvaluationResult

    fun generateQuestions(
        techStack: List<String>,
        targetRole: String,
        yearsOfExperience: String,
        categories: List<String>,
        personalityCount: Int,
        techCount: Int
    ): List<Map<String, String>>
}
