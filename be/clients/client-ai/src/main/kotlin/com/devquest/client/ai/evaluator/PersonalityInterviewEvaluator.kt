package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.PersonalityEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class PersonalityInterviewEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), PersonalityEvaluatorPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/personality-interview-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/personality-interview-user.st"))

    override fun evaluate(question: String, answer: String): AiEvaluationResult {
        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "question" to question,
            "answer" to wrapUserContent(answer, maxLength = 2000),
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, AiEvaluationResult::class.java)
        }
    }
}
