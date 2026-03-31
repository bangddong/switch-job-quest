package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
import com.devquest.core.domain.port.InterviewCoachPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InterviewCoachService(
    private val interviewCoachPort: InterviewCoachPort,
) {

    @Transactional(readOnly = true)
    fun startSession(jdText: String, targetRole: String): CoachSessionResult {
        return interviewCoachPort.startSession(jdText, targetRole)
    }

    @Transactional(readOnly = true)
    fun evaluateAnswer(question: String, answer: String, questionIndex: Int, totalQuestions: Int): CoachAnswerResult {
        return interviewCoachPort.evaluateAnswer(question, answer, questionIndex, totalQuestions)
    }

    @Transactional(readOnly = true)
    fun generateReport(targetRole: String, jdSummary: String, answers: List<CoachAnswerHistory>): CoachReportResult {
        return interviewCoachPort.generateReport(targetRole, jdSummary, answers)
    }
}
