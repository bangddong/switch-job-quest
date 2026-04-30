package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.BossPackageResult
import com.devquest.core.domain.port.BossPackageEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class BossPackageEvaluator(
    @Qualifier("bossChatClient") chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), BossPackageEvaluatorPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/boss-package-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/boss-package-user.st"))

    override fun evaluate(
        resumeContent: String,
        githubUrl: String,
        blogUrl: String,
        targetPosition: String
    ): BossPackageResult {
        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "targetPosition" to targetPosition,
            "githubUrl" to githubUrl,
            "blogUrl" to blogUrl.ifBlank { "미제공" },
            "resumeContent" to resumeContent,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(BossPackageResult::class.java)
        }
    }
}
