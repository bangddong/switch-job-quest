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

    private val startSystemTemplate = PromptTemplate(ClassPathResource("prompts/interview-coach-start-system.st"))
    private val startUserTemplate = PromptTemplate(ClassPathResource("prompts/interview-coach-start-user.st"))
    private val evaluateSystemTemplate = PromptTemplate(ClassPathResource("prompts/interview-coach-evaluate-system.st"))
    private val evaluateUserTemplate = PromptTemplate(ClassPathResource("prompts/interview-coach-evaluate-user.st"))
    private val reportSystemTemplate = PromptTemplate(ClassPathResource("prompts/interview-coach-report-system.st"))
    private val reportUserTemplate = PromptTemplate(ClassPathResource("prompts/interview-coach-report-user.st"))

    override fun startSession(jdText: String, targetRole: String): CoachSessionResult {
        val systemPrompt = startSystemTemplate.render()
        val userPrompt = startUserTemplate.render(mapOf(
            "targetRole" to targetRole,
            "jdText" to wrapUserContent(jdText),
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, CoachSessionResult::class.java)
        }
    }

    override fun evaluateAnswer(question: String, answer: String, questionIndex: Int, totalQuestions: Int): CoachAnswerResult {
        val systemPrompt = evaluateSystemTemplate.render()
        val userPrompt = evaluateUserTemplate.render(mapOf(
            "questionNumber" to (questionIndex + 1),
            "totalQuestions" to totalQuestions,
            "question" to question,
            "answer" to wrapUserContent(answer, maxLength = 2000),
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, CoachAnswerResult::class.java)
        }
    }

    override fun generateReport(targetRole: String, jdSummary: String, answers: List<CoachAnswerHistory>): CoachReportResult {
        val answersText = answers.mapIndexed { idx, it ->
            """
            [질문 ${idx + 1}]
            질문: ${it.question}
            답변: ${it.answer.take(1000)}
            피드백: ${it.feedback}
            """.trimIndent()
        }.joinToString("\n\n")

        val systemPrompt = reportSystemTemplate.render()
        val userPrompt = reportUserTemplate.render(mapOf(
            "targetRole" to targetRole,
            "jdSummary" to jdSummary,
            "answersText" to wrapUserContent(answersText),
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, CoachReportResult::class.java)
        }
    }
}
