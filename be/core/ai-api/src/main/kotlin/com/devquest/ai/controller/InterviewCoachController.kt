package com.devquest.ai.controller

import com.devquest.ai.controller.request.InterviewCoachAnswerRequest
import com.devquest.ai.controller.request.InterviewCoachReportRequest
import com.devquest.ai.controller.request.InterviewCoachStartRequest
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
import com.devquest.core.domain.port.InterviewCoachPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/interview-coach")
class InterviewCoachController(
    private val interviewCoachPort: InterviewCoachPort,
) {

    @PostMapping("/start")
    fun start(@RequestBody request: InterviewCoachStartRequest): CoachSessionResult =
        interviewCoachPort.startSession(request.jdText, request.targetRole)

    @PostMapping("/answer")
    fun answer(@RequestBody request: InterviewCoachAnswerRequest): CoachAnswerResult =
        interviewCoachPort.evaluateAnswer(
            request.question,
            request.answer,
            request.questionIndex,
            request.totalQuestions,
        )

    @PostMapping("/report")
    fun report(@RequestBody request: InterviewCoachReportRequest): CoachReportResult =
        interviewCoachPort.generateReport(request.targetRole, request.jdSummary, request.answers)
}
