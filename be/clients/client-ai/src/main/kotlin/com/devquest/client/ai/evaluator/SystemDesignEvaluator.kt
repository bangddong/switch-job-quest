package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.port.SystemDesignEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class SystemDesignEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), SystemDesignEvaluatorPort {

    private val template = PromptTemplate(ClassPathResource("prompts/system-design.st"))

    override fun evaluate(problemStatement: String, architectureDescription: String, considerations: List<String>): AiEvaluationResult {
        val prompt = template.render(mapOf(
            "problemStatement" to problemStatement,
            "architectureDescription" to architectureDescription,
            "considerations" to considerations.mapIndexed { i, c -> "${i + 1}. $c" }.joinToString("\n"),
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(AiEvaluationResult::class.java)
        }
    }
}
