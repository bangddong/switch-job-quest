package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.CoachAnswerHistory
import com.devquest.core.domain.model.evaluation.CoachAnswerResult
import com.devquest.core.domain.model.evaluation.CoachReportResult
import com.devquest.core.domain.model.evaluation.CoachSessionResult
import com.devquest.core.domain.port.InterviewCoachPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class InterviewCoachEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), InterviewCoachPort {

    private val startTemplate = PromptTemplate(ClassPathResource("prompts/interview-coach-start.st"))
    private val evaluateTemplate = PromptTemplate(ClassPathResource("prompts/interview-coach-evaluate.st"))
    private val reportTemplate = PromptTemplate(ClassPathResource("prompts/interview-coach-report.st"))

    override fun startSession(jdText: String, targetRole: String): CoachSessionResult {
        val prompt = startTemplate.render(mapOf(
            "targetRole" to targetRole,
            "jdText" to jdText,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(CoachSessionResult::class.java)
        }
    }

    override fun evaluateAnswer(question: String, answer: String, questionIndex: Int, totalQuestions: Int): CoachAnswerResult {
        val prompt = evaluateTemplate.render(mapOf(
            "questionNumber" to (questionIndex + 1),
            "totalQuestions" to totalQuestions,
            "question" to question,
            "answer" to answer,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(CoachAnswerResult::class.java)
        }
    }

    override fun generateReport(targetRole: String, jdSummary: String, answers: List<CoachAnswerHistory>): CoachReportResult {
        val answersText = answers.mapIndexed { idx, it ->
            """
            [질문 ${idx + 1}]
            질문: ${it.question}
            답변: ${it.answer}
            피드백: ${it.feedback}
            """.trimIndent()
        }.joinToString("\n\n")

        val prompt = reportTemplate.render(mapOf(
            "targetRole" to targetRole,
            "jdSummary" to jdSummary,
            "answersText" to answersText,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(CoachReportResult::class.java)
        }
    }
}
