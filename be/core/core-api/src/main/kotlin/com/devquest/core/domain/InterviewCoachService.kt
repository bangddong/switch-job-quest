package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
import com.devquest.core.domain.port.InterviewCoachPort
import org.springframework.stereotype.Service

@Service
class InterviewCoachService(
    private val interviewCoachPort: InterviewCoachPort,
) {

    fun startSession(jdText: String, targetRole: String): CoachSessionResult {
        return interviewCoachPort.startSession(jdText, targetRole)
    }

    fun evaluateAnswer(question: String, answer: String, questionIndex: Int, totalQuestions: Int): CoachAnswerResult {
        return interviewCoachPort.evaluateAnswer(question, answer, questionIndex, totalQuestions)
    }

    fun generateReport(targetRole: String, jdSummary: String, answers: List<CoachAnswerHistory>): CoachReportResult {
        return interviewCoachPort.generateReport(targetRole, jdSummary, answers)
    }
}
