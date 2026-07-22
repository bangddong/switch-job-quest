package com.devquest.ai.controller

import com.devquest.ai.controller.request.InterviewEvaluateRequest
import com.devquest.ai.controller.request.InterviewGenerateQuestionsRequest
import com.devquest.core.domain.model.evaluation.InterviewEvaluationResult
import com.devquest.core.domain.port.InterviewEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/interview")
class InterviewController(
    private val interviewEvaluatorPort: InterviewEvaluatorPort,
) {

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: InterviewEvaluateRequest): InterviewEvaluationResult =
        interviewEvaluatorPort.evaluate(
            request.category,
            request.question,
            request.answer,
            request.questionId,
            request.techStack,
            request.yearsOfExperience,
        )

    @PostMapping("/questions")
    fun generateQuestions(@RequestBody request: InterviewGenerateQuestionsRequest): List<Map<String, String>> =
        interviewEvaluatorPort.generateQuestions(
            request.techStack,
            request.targetRole,
            request.yearsOfExperience,
            request.categories,
            request.personalityCount,
            request.techCount,
        )
}
