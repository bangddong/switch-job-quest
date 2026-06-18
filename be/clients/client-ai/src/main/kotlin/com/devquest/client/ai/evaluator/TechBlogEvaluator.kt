package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.BlogEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class TechBlogEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), BlogEvaluatorPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/tech-blog-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/tech-blog-user.st"))

    override fun evaluate(techTopic: String, title: String, content: String): AiEvaluationResult {
        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "techTopic" to techTopic,
            "title" to wrapUserContent(title),
            "content" to wrapUserContent(content),
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, AiEvaluationResult::class.java)
        }
    }
}
