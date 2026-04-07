package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
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
    @Qualifier("bossChatClient") private val chatClient: ChatClient,
    private val aiCallExecutor: AiCallExecutor
) : InterviewEvaluatorPort {

    private val objectMapper = jacksonObjectMapper()

    private val log = LoggerFactory.getLogger(javaClass)

    private val evaluateTemplate = PromptTemplate(ClassPathResource("prompts/mock-interview.st"))
    private val questionsTemplate = PromptTemplate(ClassPathResource("prompts/mock-interview-questions.st"))

    override fun evaluate(category: String, question: String, answer: String, questionId: String): InterviewEvaluationResult {
        val prompt = evaluateTemplate.render(mapOf(
            "category" to category,
            "question" to question,
            "answer" to answer,
            "questionId" to questionId,
            "answerPreview" to answer.take(100),
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(InterviewEvaluationResult::class.java)
        }
    }

    override fun generateQuestions(categories: List<String>, count: Int): List<Map<String, String>> {
        val prompt = questionsTemplate.render(mapOf(
            "categories" to categories.joinToString(", "),
            "count" to count,
        ))

        val response = chatClient.prompt()
            .user(prompt)
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
