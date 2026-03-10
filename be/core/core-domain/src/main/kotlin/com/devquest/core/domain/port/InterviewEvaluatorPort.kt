package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.InterviewEvaluationResult

interface InterviewEvaluatorPort {
    fun evaluate(category: String, question: String, answer: String, questionId: String): InterviewEvaluationResult
    fun generateQuestions(categories: List<String>, count: Int): List<Map<String, String>>
}
