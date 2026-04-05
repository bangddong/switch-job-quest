package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.CoachAnswerRequestDto
import com.devquest.core.api.controller.v1.request.CoachReportRequestDto
import com.devquest.core.api.controller.v1.request.CoachSessionStartRequestDto
import com.devquest.core.domain.InterviewCoachService
import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coach")
class InterviewCoachController(
    private val interviewCoachService: InterviewCoachService,
) {
    @PostMapping("/session/start")
    fun startSession(@Valid @RequestBody request: CoachSessionStartRequestDto): ApiResponse<*> {
        return ApiResponse.success(interviewCoachService.startSession(request.jdText, request.targetRole))
    }

    @PostMapping("/session/answer")
    fun submitAnswer(@Valid @RequestBody request: CoachAnswerRequestDto): ApiResponse<*> {
        return ApiResponse.success(
            interviewCoachService.evaluateAnswer(
                request.question,
                request.answer,
                request.questionIndex,
                request.totalQuestions,
            )
        )
    }

    @PostMapping("/session/report")
    fun generateReport(@Valid @RequestBody request: CoachReportRequestDto): ApiResponse<*> {
        val answers = request.answers.map {
            CoachAnswerHistory(question = it.question, answer = it.answer, feedback = it.feedback)
        }
        return ApiResponse.success(
            interviewCoachService.generateReport(request.targetRole, request.jdSummary, answers)
        )
    }
}
