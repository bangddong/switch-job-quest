package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.TechInterviewResult

interface TechInterviewPort {
    fun generateQuestions(techStack: String): TechInterviewResult
    fun evaluate(techStack: String, questions: List<String>, answers: List<String>): TechInterviewResult
    fun generateDailyQuestion(techStack: String, recentQuestions: List<String> = emptyList()): String
}
