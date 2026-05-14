package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.domain.model.evaluation.InterviewEvaluationResult
import com.devquest.core.domain.port.InterviewEvaluatorPort
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class MockInterviewEvaluator(
    @Qualifier("bossChatClient") chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), InterviewEvaluatorPort {

    private val objectMapper = jacksonObjectMapper()

    private val log = LoggerFactory.getLogger(javaClass)

    private val evaluateSystemTemplate = PromptTemplate(ClassPathResource("prompts/mock-interview-system.st"))
    private val evaluateUserTemplate = PromptTemplate(ClassPathResource("prompts/mock-interview-user.st"))
    private val questionsSystemTemplate = PromptTemplate(ClassPathResource("prompts/mock-interview-questions-system.st"))
    private val questionsUserTemplate = PromptTemplate(ClassPathResource("prompts/mock-interview-questions-user.st"))

    override fun evaluate(
        category: String,
        question: String,
        answer: String,
        questionId: String,
        techStack: List<String>,
        yearsOfExperience: String
    ): InterviewEvaluationResult {
        val systemPrompt = evaluateSystemTemplate.render()
        val userPrompt = evaluateUserTemplate.render(mapOf(
            "category" to category,
            "question" to question,
            "answer" to answer,
            "questionId" to questionId,
            "answerPreview" to answer.take(100),
            "techStack" to techStack.joinToString(", "),
            "yearsOfExperience" to yearsOfExperience,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(InterviewEvaluationResult::class.java)
        }
    }

    override fun generateQuestions(
        techStack: List<String>,
        targetRole: String,
        yearsOfExperience: String,
        categories: List<String>,
        personalityCount: Int,
        techCount: Int
    ): List<Map<String, String>> {
        val systemPrompt = questionsSystemTemplate.render()
        val userPrompt = questionsUserTemplate.render(mapOf(
            "techStack" to techStack.joinToString(", "),
            "targetRole" to targetRole,
            "yearsOfExperience" to yearsOfExperience,
            "categories" to categories.joinToString(", "),
            "personalityCount" to personalityCount,
            "techCount" to techCount,
            "totalCount" to (techCount + personalityCount),
        ))

        val response = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content() ?: "[]"

        return try {
            objectMapper.readValue<List<Map<String, String>>>(response)
        } catch (e: Exception) {
            log.warn("질문 생성 파싱 실패: ${e.message}")
            emptyList()
        }
    }
}
