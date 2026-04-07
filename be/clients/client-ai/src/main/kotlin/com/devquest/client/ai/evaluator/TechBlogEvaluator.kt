package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.BlogEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class TechBlogEvaluator(
    private val chatClient: ChatClient,
    private val aiCallExecutor: AiCallExecutor
) : BlogEvaluatorPort {

    private val template = PromptTemplate(ClassPathResource("prompts/tech-blog.st"))

    override fun evaluate(techTopic: String, title: String, content: String): AiEvaluationResult {
        val prompt = template.render(mapOf(
            "techTopic" to techTopic,
            "title" to title,
            "content" to content,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(AiEvaluationResult::class.java)
        }
    }
}
