package com.devquest.core.domain.port

import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
import com.devquest.core.domain.port.ai.AiEvaluatorPort

interface InterviewCoachPort : AiEvaluatorPort {
    fun startSession(jdText: String, targetRole: String): CoachSessionResult
    fun evaluateAnswer(question: String, answer: String, questionIndex: Int, totalQuestions: Int): CoachAnswerResult
    fun generateReport(targetRole: String, jdSummary: String, answers: List<CoachAnswerHistory>): CoachReportResult
}
